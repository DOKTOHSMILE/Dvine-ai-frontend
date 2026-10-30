// DVINE's on-device language model: llama.cpp running a small open model (Qwen3 1.7B, GGUF) on the phone's CPU.
// Plain C++ (no JNI) so it is also built and tested on a desktop; dvine_llm_jni.cpp is the bridge to Java.
#pragma once

#include <atomic>
#include <functional>
#include <string>
#include <vector>

struct llama_model;
struct llama_context;
struct llama_sampler;
struct llama_batch_ext;

namespace dvine {

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

    // Stops a reply in progress at the next token (safe from any thread).
    void cancel() { stop_ = true; }

    const std::string & error() const { return error_; }
    bool loaded() const { return ctx_ != nullptr; }

private:
    bool decode(const std::vector<int> & tokens, int pos0);
    std::vector<int> tokenize(const std::string & text, bool add_special);
    std::string format(const std::string & system, const std::vector<Turn> & turns, bool add_assistant);

    llama_model * model_ = nullptr;
    llama_context * ctx_ = nullptr;
    llama_sampler * sampler_ = nullptr;
    llama_batch_ext * batch_ = nullptr;
    float sampler_temp_ = -1.f;
    // the system prompt already in the model's memory, kept between replies so it is not processed again
    std::string cached_system_;
    int cached_tokens_ = 0;
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

} // namespace dvine
