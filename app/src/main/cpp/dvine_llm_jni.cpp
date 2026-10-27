// The Java bridge for ai.dvine.llm.NativeLlm. Only whole UTF-8 characters are handed to Java (a token can end
// in the middle of one), converted to UTF-16 here because JNI's NewStringUTF reads modified UTF-8.
#include <jni.h>

#include "dvine_llm.h"

#include <string>
#include <vector>

namespace {

// How many bytes at the end of s are an unfinished UTF-8 character.
size_t incomplete_tail(const std::string & s) {
    size_t n = s.size();
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        unsigned char c = (unsigned char) s[n - back];
        if ((c & 0xC0) == 0x80) continue; // a continuation byte: keep looking for the lead byte
        size_t need = (c & 0x80) == 0 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 1;
        return need > back ? back : 0;
    }
    return 0;
}

jstring to_java(JNIEnv * env, const std::string & s) {
    std::vector<jchar> u;
    u.reserve(s.size());
    for (size_t i = 0; i < s.size();) {
        unsigned char c = (unsigned char) s[i];
        uint32_t cp;
        size_t len;
        if (c < 0x80) { cp = c; len = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; len = 4; }
        else { cp = 0xFFFD; len = 1; }
        if (i + len > s.size()) { cp = 0xFFFD; len = s.size() - i; }
        for (size_t k = 1; k < len; ++k) cp = (cp << 6) | ((unsigned char) s[i + k] & 0x3F);
        if (cp >= 0x10000) {
            cp -= 0x10000;
            u.push_back((jchar) (0xD800 + (cp >> 10)));
            u.push_back((jchar) (0xDC00 + (cp & 0x3FF)));
        } else {
            u.push_back((jchar) cp);
        }
        i += len;
    }
    return env->NewString(u.data(), (jsize) u.size());
}

std::string from_java(JNIEnv * env, jstring s) {
    if (!s) return std::string();
    const jchar * c = env->GetStringChars(s, nullptr);
    jsize n = env->GetStringLength(s);
    std::string out;
    for (jsize i = 0; i < n; ++i) {
        uint32_t cp = c[i];
        if (cp >= 0xD800 && cp < 0xDC00 && i + 1 < n) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (c[i + 1] - 0xDC00);
            ++i;
        }
        if (cp < 0x80) out += (char) cp;
        else if (cp < 0x800) { out += (char) (0xC0 | (cp >> 6)); out += (char) (0x80 | (cp & 0x3F)); }
        else if (cp < 0x10000) { out += (char) (0xE0 | (cp >> 12)); out += (char) (0x80 | ((cp >> 6) & 0x3F)); out += (char) (0x80 | (cp & 0x3F)); }
        else { out += (char) (0xF0 | (cp >> 18)); out += (char) (0x80 | ((cp >> 12) & 0x3F)); out += (char) (0x80 | ((cp >> 6) & 0x3F)); out += (char) (0x80 | (cp & 0x3F)); }
    }
    env->ReleaseStringChars(s, c);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_ai_dvine_llm_NativeLlm_nLoad(JNIEnv * env, jclass, jstring path, jint ctx, jint threads) {
    auto * e = new dvine::Engine();
    if (!e->load(from_java(env, path), ctx, threads)) {
        delete e;
        return 0;
    }
    return (jlong) e;
}

JNIEXPORT jstring JNICALL Java_ai_dvine_llm_NativeLlm_nReply(JNIEnv * env, jclass, jlong handle, jstring system,
                                                           jobjectArray roles, jobjectArray texts, jint max_tokens,
                                                           jfloat temperature, jobject sink) {
    auto * e = (dvine::Engine *) handle;
    if (!e) return nullptr;
    std::vector<dvine::Turn> turns;
    jsize n = env->GetArrayLength(texts);
    for (jsize i = 0; i < n; ++i) {
        auto r = (jstring) env->GetObjectArrayElement(roles, i);
        auto t = (jstring) env->GetObjectArrayElement(texts, i);
        turns.push_back({from_java(env, r), from_java(env, t)});
        env->DeleteLocalRef(r);
        env->DeleteLocalRef(t);
    }
    jmethodID on_text = nullptr;
    if (sink) {
        jclass cls = env->GetObjectClass(sink);
        on_text = env->GetMethodID(cls, "onText", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(cls);
    }
    std::string held;
    std::string all = e->reply(from_java(env, system), turns, max_tokens, temperature, [&](const std::string & piece) {
        if (!on_text) return true;
        held += piece;
        size_t tail = incomplete_tail(held);
        std::string ready = held.substr(0, held.size() - tail);
        held.erase(0, held.size() - tail);
        if (ready.empty()) return true;
        jstring js = to_java(env, ready);
        jboolean go = env->CallBooleanMethod(sink, on_text, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        return go == JNI_TRUE;
    });
    return to_java(env, all);
}

JNIEXPORT jstring JNICALL Java_ai_dvine_llm_NativeLlm_nError(JNIEnv * env, jclass, jlong handle) {
    auto * e = (dvine::Engine *) handle;
    return to_java(env, e ? e->error() : std::string("the model is not loaded"));
}

JNIEXPORT void JNICALL Java_ai_dvine_llm_NativeLlm_nCancel(JNIEnv *, jclass, jlong handle) {
    auto * e = (dvine::Engine *) handle;
    if (e) e->cancel();
}

JNIEXPORT void JNICALL Java_ai_dvine_llm_NativeLlm_nFree(JNIEnv *, jclass, jlong handle) {
    delete (dvine::Engine *) handle;
}

} // extern "C"
