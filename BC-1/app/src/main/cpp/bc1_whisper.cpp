// JNI bridge between BC-1 (com.blackice.bc1.captions.WhisperLib) and whisper.cpp.
// One native handle per loaded model. Progress and cancellation go through atomics, so whisper's
// callbacks never call back into Java (they may run while the JNI thread is busy in whisper_full).
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <string>
#include <cstring>
#include "whisper.h"

#define TAG "BC1Whisper"

namespace {
struct Handle {
    whisper_context *ctx = nullptr;
    std::atomic<int> progress{0};
    std::atomic<bool> cancel{false};
};

Handle *from(jlong h) { return reinterpret_cast<Handle *>(h); }

void onProgress(whisper_context *, whisper_state *, int progress, void *data) {
    static_cast<Handle *>(data)->progress.store(progress);
}

bool onAbort(void *data) { return static_cast<Handle *>(data)->cancel.load(); }

bool onEncoderBegin(whisper_context *, whisper_state *, void *data) { return !static_cast<Handle *>(data)->cancel.load(); }

jbyteArray bytes(JNIEnv *env, const char *text) {
    // Raw UTF-8: whisper can split a multi-byte character across tokens, which NewStringUTF would reject.
    jsize n = text ? (jsize) std::strlen(text) : 0;
    jbyteArray out = env->NewByteArray(n);
    if (n > 0) env->SetByteArrayRegion(out, 0, n, reinterpret_cast<const jbyte *>(text));
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeInit(JNIEnv *env, jclass, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, params);
    env->ReleaseStringUTFChars(path, p);
    if (!ctx) return 0;
    auto *h = new Handle();
    h->ctx = ctx;
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT void JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeFree(JNIEnv *, jclass, jlong handle) {
    Handle *h = from(handle);
    if (!h) return;
    whisper_free(h->ctx);
    delete h;
}

JNIEXPORT jint JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeTranscribe(JNIEnv *env, jclass, jlong handle, jfloatArray pcm,
        jstring language, jboolean translate, jint threads, jstring prompt) {
    Handle *h = from(handle);
    if (!h) return -100;
    h->progress.store(0);
    h->cancel.store(false);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.translate = translate;
    params.no_context = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.suppress_nst = true;          // drop "(music)", "[applause]" style non-speech tokens
    params.progress_callback = onProgress;
    params.progress_callback_user_data = h;
    params.abort_callback = onAbort;
    params.abort_callback_user_data = h;
    params.encoder_begin_callback = onEncoderBegin;
    params.encoder_begin_callback_user_data = h;

    std::string lang = "auto", initial;
    if (language) { const char *l = env->GetStringUTFChars(language, nullptr); lang = l; env->ReleaseStringUTFChars(language, l); }
    if (prompt) { const char *t = env->GetStringUTFChars(prompt, nullptr); initial = t; env->ReleaseStringUTFChars(prompt, t); }
    params.language = lang.c_str();
    params.detect_language = false;      // "auto" detects and transcribes in one pass
    params.initial_prompt = initial.empty() ? nullptr : initial.c_str();

    jsize n = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    int result = whisper_full(h->ctx, params, samples, n);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (h->cancel.load()) return -1;
    if (result != 0) __android_log_print(ANDROID_LOG_WARN, TAG, "whisper_full failed: %d", result);
    else h->progress.store(100);
    return result;
}

JNIEXPORT jint JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeProgress(JNIEnv *, jclass, jlong handle) {
    Handle *h = from(handle);
    return h ? h->progress.load() : 0;
}

JNIEXPORT void JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeCancel(JNIEnv *, jclass, jlong handle) {
    Handle *h = from(handle);
    if (h) h->cancel.store(true);
}

JNIEXPORT jint JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeSegmentCount(JNIEnv *, jclass, jlong handle) {
    return whisper_full_n_segments(from(handle)->ctx);
}

JNIEXPORT jbyteArray JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeSegmentText(JNIEnv *env, jclass, jlong handle, jint i) {
    return bytes(env, whisper_full_get_segment_text(from(handle)->ctx, i));
}

/** Segment start and end in milliseconds (whisper reports 10 ms units). */
JNIEXPORT jlongArray JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeSegmentTimes(JNIEnv *env, jclass, jlong handle, jint i) {
    jlong t[2] = {whisper_full_get_segment_t0(from(handle)->ctx, i) * 10, whisper_full_get_segment_t1(from(handle)->ctx, i) * 10};
    jlongArray out = env->NewLongArray(2);
    env->SetLongArrayRegion(out, 0, 2, t);
    return out;
}

JNIEXPORT jstring JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeLanguage(JNIEnv *env, jclass, jlong handle) {
    int id = whisper_full_lang_id(from(handle)->ctx);
    const char *s = id >= 0 ? whisper_lang_str(id) : nullptr;
    return env->NewStringUTF(s ? s : "");
}

JNIEXPORT jstring JNICALL Java_com_blackice_bc1_captions_WhisperLib_nativeSystemInfo(JNIEnv *env, jclass) {
    return env->NewStringUTF(whisper_print_system_info());
}

}  // extern "C"
