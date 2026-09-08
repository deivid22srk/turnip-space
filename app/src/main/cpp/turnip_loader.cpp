// turnip_loader.cpp — native driver loading bridge for Turnip Space.
//
// Design notes (see ARCHITECTURE.md):
//  * The real AdrenoTools library exposes adrenotools_open_libvulkan() which
//    hooks dlopen/android_dlopen_ext inside the HOST process so that the
//    hosted engine's `dlopen("libvulkan.so")` resolves to a custom
//    libvulkan_freedreno.so (Turnip). Emulators (Skyline, Strato, Dolphin,
//    PPSSPP...) integrate that library into their own source.
//  * This loader is our in-process equivalent for the virtual engine:
//    it dlopens the extracted Turnip driver from the app sandbox BEFORE the
//    plugin's native code runs and validates the ICD entrypoints. The virtual
//    engine routes its Vulkan acquisition through this bridge, which keeps
//    everything inside our own process — the only scenario where a custom
//    driver can be applied without root.
//  * dlopen happens only on explicit user launch, after the zip was already
//    validated crash-safely (pure ELF parsing in Kotlin — ElfSymbolParser).

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <android/dlext.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <mutex>

#define LOG_TAG "TurnipLoader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Handles currently held open. dlclose() of Vulkan ICDs that registered
// themselves with the loader can be unsafe; we therefore keep handles open
// for the process lifetime and never dlclose them (same policy as
// AdrenoTools' host apps). The map exists so we can report and dedupe.
std::mutex g_handlesMutex;
std::vector<void *> g_openHandles;

const char *kIcdSymbols[] = {
    "vk_icdGetInstanceProcAddr",
    "vk_icdNegotiateLoaderICDInterfaceVersion",
    "vkEnumerateInstanceExtensions",
};

// Returns nullptr on success, otherwise a malloc'ed C string with the error
// (caller frees). Also verifies the ICD entrypoints are exported.
char *openDriverChecked(const char *driverPath, void **outHandle) {
    *outHandle = nullptr;
    // RTLD_LOCAL: do not pollute the global namespace. RTLD_NOW so missing
    // symbols surface immediately instead of exploding at first call.
    void *handle = dlopen(driverPath, RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        const char *err = dlerror();
        std::string msg = "dlopen failed: ";
        msg += err ? err : "unknown dlerror";
        return strdup(msg.c_str());
    }
    for (const char *sym : kIcdSymbols) {
        void *p = dlsym(handle, sym);
        LOGI("symbol %s -> %p", sym, p);
        // vk_icdNegotiateLoaderICDInterfaceVersion is the strongest signal;
        // freedreno/Turnip always exports it. Require at least the ICD getter.
        if (std::strcmp(sym, "vk_icdGetInstanceProcAddr") == 0 && !p) {
            dlclose(handle);
            return strdup("driver does not export vk_icdGetInstanceProcAddr");
        }
    }
    *outHandle = handle;
    return nullptr;
}

} // namespace

extern "C" {

/**
 * Opens the driver .so and keeps the handle alive for the process lifetime.
 * Returns 0 on success. On failure returns 1 and writes the error message
 * into `outError` (jstring). Never throws: the Kotlin side decides how to
 * surface the failure to the UI.
 */
JNIEXPORT jint JNICALL
Java_io_github_deivid22srk_turnipspace_data_driver_NativeDriverLoader_openDriver(
    JNIEnv *env, jclass /*clazz*/, jstring jDriverPath, jobjectArray jOutError) {
    if (!jDriverPath) return 1;

    const char *path = env->GetStringUTFChars(jDriverPath, nullptr);
    if (!path) return 1;

    void *handle = nullptr;
    char *error = openDriverChecked(path, &handle);
    env->ReleaseStringUTFChars(jDriverPath, path);

    if (error) {
        LOGE("openDriver failed: %s", error);
        if (jOutError && env->GetArrayLength(jOutError) > 0) {
            jstring jErr = env->NewStringUTF(error);
            env->SetObjectArrayElement(jOutError, 0, jErr);
        }
        free(error);
        return 1;
    }

    size_t handleCount = 0;
    {
        std::lock_guard<std::mutex> lock(g_handlesMutex);
        // Dedupe: same driver may be opened twice across launches.
        for (void *h : g_openHandles) {
            if (h == handle) {
                LOGI("driver already tracked, reusing handle");
                return 0;
            }
        }
        g_openHandles.push_back(handle);
        handleCount = g_openHandles.size();
    }
    LOGI("driver opened and preloaded into process: %zu total handles", handleCount);
    return 0;
}

/**
 * Returns true (1) if the given path can be dlopen'ed and exposes the ICD
 * entrypoints. This is the *runtime* check used right before launching a
 * space (zip-level validation already happened crash-safely in Kotlin).
 */
JNIEXPORT jboolean JNICALL
Java_io_github_deivid22srk_turnipspace_data_driver_NativeDriverLoader_canOpenDriver(
    JNIEnv *env, jclass /*clazz*/, jstring jDriverPath) {
    if (!jDriverPath) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(jDriverPath, nullptr);
    if (!path) return JNI_FALSE;

    void *handle = dlopen(path, RTLD_LAZY | RTLD_LOCAL);
    jboolean ok = JNI_FALSE;
    if (handle) {
        ok = dlsym(handle, "vk_icdGetInstanceProcAddr") != nullptr ? JNI_TRUE : JNI_FALSE;
        // NOTE: we intentionally dlclose here — this runs in the launch path
        // and openDriver() will re-open with RTLD_NOW afterwards.
        dlclose(handle);
    } else {
        const char *err = dlerror();
        LOGW("canOpenDriver dlopen(%s) failed: %s", path, err ? err : "?");
    }
    env->ReleaseStringUTFChars(jDriverPath, path);
    return ok;
}

} // extern "C"
