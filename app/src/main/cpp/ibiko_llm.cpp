#include "ibiko_llm.h"

#include "llama.h"

#include <algorithm>
#include <cstring>

namespace ibiko {

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
    // Map the file. Do not mlock it: locking pins the whole GGUF in RAM even between replies.
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;
    model_ = llama_model_load_from_file(path.c_str(), mp);
    if (!model_) {
        error_ = "the model file could not be read";
        return false;
    }
    // A wide batch is the other thing that pins every core at once. 256 is enough for a phone and stays quieter.
    int batch = n_ctx <= 2048 ? 64 : 128;
    if (n_threads > 2) n_threads = 2;
    if (n_threads < 1) n_threads = 1;
    if (batch > n_ctx) batch = n_ctx;
    if (batch < 32) batch = 32;
    batch_tokens_ = batch;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx;
    cp.n_batch = batch;
    cp.n_ubatch = batch;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    cp.no_perf = true; // no timing records: nothing reads them
    cp.n_seq_max = SLOTS; // several conversations kept side by side (see SLOTS)
    cp.kv_unified = true; // sharing one memory of n_ctx cells: no more memory than one
    cp.offload_kqv = false; // no GPU: don't reserve a device copy of the cache
    cp.op_offload = false;
    // The cache (the model's memory of the conversation) is 8-bit with flash attention at every size: about half
    // the memory of 16-bit (a 1.7B model at 8,192 tokens saves roughly 470 MB, at 2,048 about 110 MB). It used to
    // stay 16-bit below 4,096 tokens because it wrote a little faster there; memory is what a phone runs out of
    // first, so the smaller cache wins. If the 8-bit memory can't be made, 16-bit is used.
    cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    cp.type_k = GGML_TYPE_Q8_0;
    cp.type_v = GGML_TYPE_Q8_0;
    ctx_ = llama_init_from_model(model_, cp);
    if (!ctx_) {
        cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
        cp.type_k = GGML_TYPE_F16;
        cp.type_v = GGML_TYPE_F16;
        ctx_ = llama_init_from_model(model_, cp);
    }
    if (!ctx_) {
        error_ = "not enough memory for the model's context";
        llama_model_free(model_);
        model_ = nullptr;
        return false;
    }
    batch_ = llama_batch_ext_init(ctx_);
    for (int i = 0; i < SLOTS; ++i) {
        cached_[i].clear(); // a new context starts with nothing in memory
        used_[i] = 0;
    }
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
    std::string plain = chatml(system, turns, add_assistant);
    const char * tmpl = model_ ? llama_model_chat_template(model_, nullptr) : nullptr;
    if (tmpl == nullptr || tmpl[0] == '\0') {
        return plain; // a GGUF with no template still answers (ChatML). Known families keep their own template.
    }
    std::vector<llama_chat_message> msgs;
    msgs.push_back({"system", system.c_str()});
    for (const Turn & t : turns) {
        msgs.push_back({t.role.c_str(), t.text.c_str()});
    }
    std::vector<char> buf(4096);
    int n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
    if (n > (int) buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), add_assistant, buf.data(), (int32_t) buf.size());
    }
    return n < 0 ? plain : std::string(buf.data(), n);
}

// A model file with no chat template (or one llama.cpp refuses) still has to be asked something.
// ChatML is what Qwen and many other GGUF files already use; a file that has its own template never gets here.
std::string Engine::chatml(const std::string & system, const std::vector<Turn> & turns, bool add_assistant) {
    std::string out = "<|im_start|>system\n" + system + "<|im_end|>\n";
    for (const Turn & t : turns) {
        std::string role = t.role == "assistant" ? "assistant" : "user";
        out += "<|im_start|>" + role + "\n" + t.text + "<|im_end|>\n";
    }
    if (add_assistant) {
        out += "<|im_start|>assistant\n";
    }
    return out;
}

// Feeds tokens at positions pos0.. in chunks of the batch size; logits only for the last one.
bool Engine::decode(const std::vector<int> & tokens, int pos0, int seq) {
    const int chunk = batch_tokens_ > 0 ? batch_tokens_ : 128;
    for (size_t i = 0; i < tokens.size() && !stop_; i += chunk) {
        size_t end = std::min(tokens.size(), i + chunk);
        llama_batch_ext_clear(batch_);
        for (size_t k = i; k < end; ++k) {
            int32_t idx = llama_batch_ext_add_token(batch_, seq, tokens[k]);
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

void Engine::drop_slot(int slot) {
    llama_memory_seq_rm(llama_get_memory(ctx_), slot, -1, -1);
    cached_[slot].clear();
    used_[slot] = 0;
}

int Engine::pick_slot(const std::vector<int> & tokens, size_t sys_len, size_t * shared) {
    int best = -1, oldest = 0;
    size_t best_keep = 0;
    for (int i = 0; i < SLOTS; ++i) {
        size_t keep = 0;
        while (keep < cached_[i].size() && keep < tokens.size() && cached_[i][keep] == tokens[keep]) {
            ++keep;
        }
        if (keep > best_keep) {
            best_keep = keep;
            best = i;
        }
        if (cached_[i].empty() || (!cached_[oldest].empty() && used_[i] < used_[oldest])) {
            oldest = i; // an empty slot first, then the one used longest ago
        }
    }
    // a slot that holds these instructions carries on; otherwise a free (or the stalest) slot starts afresh, so the
    // other instructions stay in memory for their next call
    if (best < 0 || best_keep < sys_len) {
        *shared = 0;
        return oldest;
    }
    *shared = best_keep;
    return best;
}

bool Engine::read_into(int slot, const std::vector<int> & tokens, size_t keep) {
    llama_memory_t mem = llama_get_memory(ctx_);
    for (int attempt = 0; attempt < 2; ++attempt) {
        if (!llama_memory_seq_rm(mem, slot, (llama_pos) keep, -1)) {
            llama_memory_seq_rm(mem, slot, -1, -1); // this model's memory can't be cut part-way: read it all
            keep = 0;
        }
        cached_[slot].resize(keep);
        std::vector<int> fresh(tokens.begin() + keep, tokens.end());
        if (decode(fresh, (int) keep, slot) && !stop_) {
            cached_[slot] = tokens;
            used_[slot] = ++clock_;
            last_reused_ = (int) keep;
            last_read_ = (int) fresh.size();
            return true;
        }
        if (stop_) {
            break;
        }
        // memory full: the other conversations are let go, and this one is read again
        for (int i = 0; i < SLOTS; ++i) {
            if (i != slot) drop_slot(i);
        }
    }
    drop_slot(slot); // part-read (or stopped while reading): this slot starts clean next time
    return false;
}

bool Engine::warm(const std::string & system) {
    stop_ = false;
    if (!ctx_) {
        return false;
    }
    std::vector<int> tokens = tokenize(format(system, {}, false), true);
    if (tokens.empty()) {
        return false;
    }
    size_t shared = 0;
    int slot = pick_slot(tokens, tokens.size(), &shared);
    if (shared >= tokens.size()) {
        used_[slot] = ++clock_;
        return true; // already in memory
    }
    return read_into(slot, tokens, shared);
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
    // Everything already in the model's memory from the last reply (the persona, the chat so far, the reply it
    // wrote) is kept: only the part of the prompt after the longest shared beginning is read again. A long chat or
    // an agent's growing work is read once, not on every reply.
    std::vector<int> tokens = tokenize(sys_text, true);
    std::vector<int> rest = tokenize(full.substr(sys_text.size()), false);
    tokens.insert(tokens.end(), rest.begin(), rest.end());
    const int n_ctx = (int) llama_n_ctx(ctx_);
    if ((int) tokens.size() + max_tokens > n_ctx) {
        error_ = "the conversation is too long for the model's memory";
        return std::string();
    }
    size_t keep = 0;
    const int slot = pick_slot(tokens, tokenize(sys_text, true).size(), &keep);
    if (keep == tokens.size() && keep > 0) {
        --keep; // the last token is read again so there is something to answer from
    }
    if (!read_into(slot, tokens, keep)) {
        if (!stop_) error_ = "the model could not read the question";
        return std::string();
    }
    const llama_vocab * vocab = llama_model_get_vocab(model_);
    int pos = (int) tokens.size();
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
        if (!decode(one, pos++, slot)) {
            drop_slot(slot); // memory ran out mid-reply: this slot starts clean next time
            break;
        }
        cached_[slot].push_back(tok); // the reply stays in memory too: the next turn starts with it
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

} // namespace ibiko
