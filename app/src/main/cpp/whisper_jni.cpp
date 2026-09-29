#include <jni.h>
#include <atomic>
#include <algorithm>
#include <string>
#include <thread>
#include <vector>
#include "whisper.h"

static std::atomic<bool> cancelled{false};
static bool abort_inference(void *) { return cancelled.load(); }
static void quiet_log(enum ggml_log_level, const char *, void *) {}

extern "C" JNIEXPORT void JNICALL
Java_fr_azks_clavier_WhisperNative_cancel(JNIEnv *, jobject) { cancelled.store(true); }

extern "C" JNIEXPORT void JNICALL
Java_fr_azks_clavier_WhisperNative_prepare(JNIEnv *, jobject) { cancelled.store(false); }

extern "C" JNIEXPORT jbyteArray JNICALL
Java_fr_azks_clavier_WhisperNative_transcribe(JNIEnv *env, jobject, jstring path, jfloatArray audio, jstring language) {
    whisper_log_set(quiet_log, nullptr);
    const jsize size = env->GetArrayLength(audio);
    if (size < 1600 || size > 16000 * 60 || cancelled.load()) return env->NewByteArray(0);
    const char *model = env->GetStringUTFChars(path, nullptr);
    if (!model) return nullptr;
    auto options = whisper_context_default_params();
    options.use_gpu = false;
    auto *ctx = whisper_init_from_file_with_params(model, options);
    env->ReleaseStringUTFChars(path, model);
    if (!ctx) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Impossible de charger le modele Whisper.");
        return nullptr;
    }
    std::vector<float> samples(size);
    env->GetFloatArrayRegion(audio, 0, size, samples.data());
    const char *lang = env->GetStringUTFChars(language, nullptr);
    if (!lang) { whisper_free(ctx); return nullptr; }
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::min(4u, std::max(1u, std::thread::hardware_concurrency()));
    params.language = lang;
    params.translate = false;
    params.no_context = true;
    params.print_progress = params.print_realtime = params.print_timestamps = params.print_special = false;
    params.abort_callback = abort_inference;
    std::string output;
    int result = cancelled.load() ? -1 : whisper_full(ctx, params, samples.data(), size);
    if (result == 0 && !cancelled.load()) {
        for (int i = 0; i < whisper_full_n_segments(ctx); ++i) output += whisper_full_get_segment_text(ctx, i);
    }
    env->ReleaseStringUTFChars(language, lang);
    whisper_free(ctx);
    std::fill(samples.begin(), samples.end(), 0.0f);
    if (result != 0 && !cancelled.load()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "La transcription Whisper a echoue.");
        return nullptr;
    }
    auto bytes = env->NewByteArray(static_cast<jsize>(output.size()));
    if (bytes) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(output.size()), reinterpret_cast<const jbyte *>(output.data()));
    return bytes;
}
