// Ibiko's on-device language model: llama.cpp running a small open model (Qwen3 1.7B, GGUF) on the phone's CPU.
// Plain C++ (no JNI) so it is also built and tested on a desktop; ibiko_llm_jni.cpp is the bridge to Java.
#pragma once

#include <atomic>
#include <functional>
#include <string>
#include <vector>

struct llama_model;
struct llama_context;
struct llama_sampler;
struct llama_batch_ext;

namespace ibiko {

struct Turn {
    std::string role; // "user" or "assistant"
    std::string text;
};

class Engine {
public:
    ~Engine();

    // Loads the model file; false (and why in error()) when it can't.
    bool load(const std::string & path, int n_ctx, int n_threads);

    // One answer to the last user turn: the persona's system prompt, the recent turns, then the reply streamed
    // piece by piece to on_text (return false from it to stop). The hidden <think> block is never passed on.
    // Returns the whole visible reply.
    std::string reply(const std::string & system, const std::vector<Turn> & turns, int max_tokens, float temperature,
                      const std::function<bool(const std::string &)> & on_text);

    // Reads the persona's instructions into memory ahead of the first question, so the first reply starts at once.
    // Nothing is done when memory already starts with them. False when they couldn't be read.
    bool warm(const std::string & system);

    // Stops a reply in progress at the next token (safe from any thread).
    void cancel() { stop_ = true; }

    const std::string & error() const { return error_; }
    // From the last reply: how many prompt tokens were already in memory, and how many had to be read.
    int last_reused() const { return last_reused_; }
    int last_read() const { return last_read_; }
    bool loaded() const { return ctx_ != nullptr; }

private:
    bool decode(const std::vector<int> & tokens, int pos0, int seq);
    // Reads tokens into a slot; when memory is full, the other slots are let go and it is tried once more.
    bool read_into(int slot, const std::vector<int> & tokens, size_t keep);
    // The slot whose memory starts most like these tokens (or, if none shares the instructions, the one used longest ago).
    int pick_slot(const std::vector<int> & tokens, size_t sys_len, size_t * shared);
    void drop_slot(int slot);
    std::vector<int> tokenize(const std::string & text, bool add_special);
    std::string format(const std::string & system, const std::vector<Turn> & turns, bool add_assistant);
    std::string chatml(const std::string & system, const std::vector<Turn> & turns, bool add_assistant);

    llama_model * model_ = nullptr;
    llama_context * ctx_ = nullptr;
    llama_sampler * sampler_ = nullptr;
    llama_batch_ext * batch_ = nullptr;
    int batch_tokens_ = 512; // prefill chunk; kept at or under the context's n_batch so the graph stays small
    float sampler_temp_ = -1.f;
    // The model's memory holds up to SLOTS conversations side by side (each a sequence): the tokens in each (its
    // prompt and the reply written), kept between replies so a prompt that starts the same way is not read again.
    // Two calls with different instructions (the routing call, then the persona's answer) each keep theirs, instead
    // of pushing the other out and reading a thousand tokens of instructions again every time.
    static const int SLOTS = 3;
    std::vector<int> cached_[SLOTS];
    unsigned long used_[SLOTS] = {0, 0, 0};
    unsigned long clock_ = 0;
    int last_reused_ = 0, last_read_ = 0;
    std::atomic<bool> stop_{false};
    std::string error_;
};

// Drops a <think>...</think> block (Qwen3's hidden reasoning) from streamed text, piece by piece.
class ThinkFilter {
public:
    std::string feed(const std::string & piece);
    bool thinking() const { return inside_; }

private:
    std::string pending_;
    bool inside_ = false;
    bool started_ = false;
};

} // namespace ibiko
