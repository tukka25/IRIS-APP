#include <android/log.h>
#include <jni.h>
#include <llama.h>

#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#define LOG_TAG "GemmaLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct LlamaSession {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    int max_tokens = 96;
    std::mutex mutex;

    ~LlamaSession() {
        if (sampler != nullptr) {
            llama_sampler_free(sampler);
        }
        if (context != nullptr) {
            llama_free(context);
        }
        if (model != nullptr) {
            llama_model_free(model);
        }
    }
};

std::string to_string(JNIEnv * env, jstring value) {
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        throw std::runtime_error("Failed to read Java string");
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void throw_illegal_state(JNIEnv * env, const std::string & message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message.c_str());
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text) {
    const int token_count = -llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        nullptr,
        0,
        true,
        true
    );
    if (token_count <= 0) {
        throw std::runtime_error("Failed to count prompt tokens");
    }

    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    const int actual_count = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true
    );
    if (actual_count < 0) {
        throw std::runtime_error("Failed to tokenize prompt");
    }
    tokens.resize(static_cast<size_t>(actual_count));
    return tokens;
}

std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(128);
    int size = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    if (size < 0) {
        buffer.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    }
    if (size < 0) {
        throw std::runtime_error("Failed to convert token to text");
    }
    return std::string(buffer.data(), static_cast<size_t>(size));
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_gemmaworkflow_platform_inference_llama_LlamaCppEngine_nativeInit(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jint context_size,
    jint max_tokens,
    jint gpu_layers
) {
    try {
        ggml_backend_load_all();

        auto session = std::make_unique<LlamaSession>();
        session->max_tokens = max_tokens;

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = gpu_layers;

        const std::string path = to_string(env, model_path);
        LOGI("Loading model from %s", path.c_str());
        session->model = llama_model_load_from_file(path.c_str(), model_params);
        if (session->model == nullptr) {
            throw std::runtime_error("Unable to load GGUF model");
        }

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = context_size;
        context_params.n_batch = context_size;
        context_params.no_perf = false;

        session->context = llama_init_from_model(session->model, context_params);
        if (session->context == nullptr) {
            throw std::runtime_error("Unable to create llama context");
        }

        auto sampler_params = llama_sampler_chain_default_params();
        sampler_params.no_perf = false;
        session->sampler = llama_sampler_chain_init(sampler_params);
        llama_sampler_chain_add(session->sampler, llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(session->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        return reinterpret_cast<jlong>(session.release());
    } catch (const std::exception & exception) {
        throw_illegal_state(env, exception.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gemmaworkflow_platform_inference_llama_LlamaCppEngine_nativeGenerate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jstring prompt
) {
    auto * session = reinterpret_cast<LlamaSession *>(handle);
    if (session == nullptr) {
        throw_illegal_state(env, "Model is not loaded");
        return env->NewStringUTF("");
    }

    try {
        std::lock_guard<std::mutex> lock(session->mutex);

        const llama_vocab * vocab = llama_model_get_vocab(session->model);
        const std::string prompt_text = to_string(env, prompt);
        std::vector<llama_token> prompt_tokens = tokenize(vocab, prompt_text);

        const uint32_t context_size = llama_n_ctx(session->context);
        if (prompt_tokens.size() + static_cast<size_t>(session->max_tokens) > context_size) {
            throw std::runtime_error("Prompt plus max tokens exceeds the llama context size");
        }

        llama_memory_clear(llama_get_memory(session->context), true);
        llama_sampler_reset(session->sampler);

        llama_batch batch = llama_batch_get_one(prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()));
        if (llama_decode(session->context, batch) != 0) {
            throw std::runtime_error("Failed to decode prompt");
        }

        std::string output;
        output.reserve(1024);

        for (int i = 0; i < session->max_tokens; ++i) {
            llama_token token = llama_sampler_sample(session->sampler, session->context, -1);
            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }

            llama_sampler_accept(session->sampler, token);
            output += token_to_piece(vocab, token);

            batch = llama_batch_get_one(&token, 1);
            if (llama_decode(session->context, batch) != 0) {
                throw std::runtime_error("Failed to decode generated token");
            }
        }

        return env->NewStringUTF(output.c_str());
    } catch (const std::exception & exception) {
        throw_illegal_state(env, exception.what());
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gemmaworkflow_platform_inference_llama_LlamaCppEngine_nativeFree(
    JNIEnv *,
    jobject,
    jlong handle
) {
    delete reinterpret_cast<LlamaSession *>(handle);
}
