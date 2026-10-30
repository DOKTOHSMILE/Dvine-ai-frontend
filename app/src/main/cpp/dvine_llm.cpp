#include "dvine_llm.h"

#include "llama.h"

#include <algorithm>
#include <cstring>

namespace dvine {

namespace {
bool backend_ready = false;
}

Engine::~Engine() {
    if (batch_) llama_batch_ext_free(batch_);
    if (sampler_) llama_sampler_free(sampler_);
    if (ctx_) llama_free(ctx_);
    if (model_) llama_model_free(model_);
}

bool Engine::load(const std::string & path, int n_ctx, int n_threads) {
    if (!backend_ready) {
        llama_log_set([](enum ggml_log_level, const char *, void *) {}, nullptr);
        llama_backend_init();
        backend_ready = true;
    }
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU only: works on every phone
    model_ = llama_model_load_from_file(path.c_str(), mp);
    if (!model_) {
        error_ = "the model file could not be read";
        return false;
    }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx;
    cp.n_batch = 512;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    ctx_ = llama_init_from_model(model_, cp);
    if (!ctx_) {
        error_ = "not enough memory for the model's context";
        llama_model_free(model_);
        model_ = nullptr;
        return false;
    }
    batch_ = llama_batch_ext_init(ctx_);
    return true;
}

std::vector<int> Engine::tokenize(const std::string & text, bool add_special) {
    const llama_vocab * vocab = llama_model_get_vocab(model_);
    int n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, add_special, true);
    std::vector<int> out(std::max(n, 0));
    if (n > 0 && llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), out.data(), n, add_special, true) < 0) {
        out.clear();
    }
    return out;
}

std::string Engine::format(const std::string & system, const std::vector<Turn> & turns, bool add_assistant) {
    std::vector<llama_chat_message> msgs;
    msgs.push_back({"system", system.c_str()});
    for (const Turn & t : turns) {
        msgs.push_back({t.role.c_str(), t.text.c_str()});
    }
    const char * tmpl = llama_model_chat_template(model_, nullptr);
    std::vector<char> buf(4096);
    int n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
    if (n > (int) buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
    }
    return n < 0 ? std::string() : std::string(buf.data(), n);
}

// Feeds tokens at positions pos0.. in chunks of the batch size; logits only for the last one.
bool Engine::decode(const std::vector<int> & tokens, int pos0) {
    const int chunk = 512;
    for (size_t i = 0; i < tokens.size(); i += chunk) {
        size_t end = std::min(tokens.size(), i + chunk);
        llama_batch_ext_clear(batch_);
        for (size_t k = i; k < end; ++k) {
            int32_t idx = llama_batch_ext_add_token(batch_, 0, tokens[k]);
            llama_pos pos = pos0 + (llama_pos) k;
            llama_batch_ext_set_pos(batch_, idx, &pos);
        }
        if (end == tokens.size()) {
            llama_batch_ext_set_output_logits(batch_, (int32_t) (end - i - 1), true);
        }
        if (llama_process(ctx_, LLAMA_PROCESS_TYPE_DECODE, batch_) != 0) {
            return false;
        }
    }
    return true;
}

std::string Engine::reply(const std::string & system, const std::vector<Turn> & turns, int max_tokens, float temperature,
                          const std::function<bool(const std::string &)> & on_text) {
    stop_ = false;
    if (!ctx_) {
        error_ = "the model is not loaded";
        return std::string();
    }
    if (!sampler_ || sampler_temp_ != temperature) {
        if (sampler_) llama_sampler_free(sampler_);
        sampler_ = llama_sampler_chain_init(llama_sampler_chain_default_params());
        const llama_vocab * vocab = llama_model_get_vocab(model_);
        llama_sampler_chain_add(sampler_, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.1f, 0.f, 0.f));
        llama_sampler_chain_add(sampler_, llama_sampler_init_top_k(20));
        llama_sampler_chain_add(sampler_, llama_sampler_init_top_p(0.8f, 1));
        llama_sampler_chain_add(sampler_, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler_, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        sampler_temp_ = temperature;
    }
    llama_sampler_reset(sampler_);
    llama_memory_t mem = llama_get_memory(ctx_);
    // the system prompt alone is a prefix of the whole prompt: keep it in memory between replies
    std::string sys_text = format(system, {}, false);
    std::string full = format(system, turns, true);
    if (full.empty() || full.compare(0, sys_text.size(), sys_text) != 0) {
        error_ = "the chat template could not be applied";
        return std::string();
    }
    // Qwen3 reasons in a hidden block unless the assistant turn already closed it.
    // An empty block makes the answer start immediately. /no_think is the request for that.
    if (system.find("/no_think") != std::string::npos) {
        const std::string head = "<|im_start|>assistant\n";
        if (full.size() >= head.size() && full.compare(full.size() - head.size(), head.size(), head) == 0) {
            full += "<think>\n\n</think>\n\n";
        }
    }
    if (cached_system_ != sys_text) {
        llama_memory_clear(mem, true);
        std::vector<int> sys_tokens = tokenize(sys_text, true);
        if (!decode(sys_tokens, 0)) {
            cached_system_.clear();
            error_ = "the model could not read the persona";
            return std::string();
        }
        cached_system_ = sys_text;
        cached_tokens_ = (int) sys_tokens.size();
    }
    llama_memory_seq_rm(mem, 0, cached_tokens_, -1);
    std::vector<int> rest = tokenize(full.substr(sys_text.size()), false);
    const int n_ctx = (int) llama_n_ctx(ctx_);
    if (cached_tokens_ + (int) rest.size() + max_tokens > n_ctx) {
        error_ = "the conversation is too long for the model's memory";
        return std::string();
    }
    if (!decode(rest, cached_tokens_)) {
        error_ = "the model could not read the question";
        return std::string();
    }
    const llama_vocab * vocab = llama_model_get_vocab(model_);
    int pos = cached_tokens_ + (int) rest.size();
    ThinkFilter filter;
    std::string out, bytes;
    for (int i = 0; i < max_tokens && !stop_; ++i) {
        llama_token tok = llama_sampler_sample(sampler_, ctx_, -1);
        if (llama_vocab_is_eog(vocab, tok)) {
            break;
        }
        char buf[256];
        int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (n > 0) {
            std::string visible = filter.feed(std::string(buf, n));
            if (!visible.empty()) {
                out += visible;
                if (on_text && !on_text(visible)) {
                    break;
                }
            } else if (filter.thinking() && on_text && (i % 8) == 0) {
                // still inside a hidden block: tell the screen, but do not speak it
                if (!on_text("\x01")) {
                    break;
                }
            }
        }
        std::vector<int> one{tok};
        if (!decode(one, pos++)) {
            break;
        }
    }
    return out;
}

std::string ThinkFilter::feed(const std::string & piece) {
    pending_ += piece;
    std::string out;
    while (true) {
        if (inside_) {
            size_t end = pending_.find("</think>");
            if (end == std::string::npos) {
                // keep only what could still be the start of the closing tag
                if (pending_.size() > 8) pending_.erase(0, pending_.size() - 8);
                return out;
            }
            pending_.erase(0, end + 8);
            inside_ = false;
            started_ = false; // the answer starts after the block (leading blank lines dropped below)
            continue;
        }
        size_t open = pending_.find("<think>");
        if (open != std::string::npos) {
            out += pending_.substr(0, open);
            pending_.erase(0, open + 7);
            inside_ = true;
            continue;
        }
        // hold back a possible partial "<think>" at the end
        size_t keep = 0;
        for (size_t k = 1; k < 7 && k <= pending_.size(); ++k) {
            if (std::string("<think>").compare(0, k, pending_, pending_.size() - k, k) == 0) keep = k;
        }
        out += pending_.substr(0, pending_.size() - keep);
        pending_.erase(0, pending_.size() - keep);
        break;
    }
    if (!started_) {
        size_t first = out.find_first_not_of(" \n\r\t");
        if (first == std::string::npos) return std::string();
        out.erase(0, first);
        started_ = true;
    }
    return out;
}

} // namespace dvine
