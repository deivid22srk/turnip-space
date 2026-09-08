// gpu_probe.cpp — reads GL_RENDERER from a minimal EGL pbuffer context.
//
// Why native: `GLES10.glGetString()` from Java requires an existing GL
// context owned by a GLSurfaceView or similar. A 1x1 offscreen pbuffer lets
// us query the Adreno model string anywhere in the app (driver manager,
// space launch diagnostics) without any UI machinery, and without touching
// any other EGL usage in the process (we never call eglTerminate).

#include <jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>

#define LOG_TAG "GpuProbe"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_deivid22srk_turnipspace_data_gpu_GpuProbe_nativeProbeRenderer(
    JNIEnv *env, jclass /*clazz*/) {

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        LOGW("eglGetDisplay returned EGL_NO_DISPLAY");
        return nullptr;
    }
    if (!eglInitialize(display, nullptr, nullptr)) {
        LOGW("eglInitialize failed: 0x%x", eglGetError());
        return nullptr;
    }

    const EGLint configAttribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint numConfigs = 0;
    if (!eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) || numConfigs < 1) {
        LOGW("eglChooseConfig failed: 0x%x", eglGetError());
        return nullptr;
    }

    const EGLint pbufferAttribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    EGLSurface surface = eglCreatePbufferSurface(display, config, pbufferAttribs);
    if (surface == EGL_NO_SURFACE) {
        LOGW("eglCreatePbufferSurface failed: 0x%x", eglGetError());
        return nullptr;
    }

    const EGLint contextAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) {
        LOGW("eglCreateContext failed: 0x%x", eglGetError());
        eglDestroySurface(display, surface);
        return nullptr;
    }

    jstring result = nullptr;
    if (eglMakeCurrent(display, surface, surface, context)) {
        const GLubyte *renderer = glGetString(GL_RENDERER);
        if (renderer) {
            result = env->NewStringUTF(reinterpret_cast<const char *>(renderer));
        }
        // Release OUR current surfaces but never terminate the display:
        // other components in this process may hold EGL resources.
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    } else {
        LOGW("eglMakeCurrent failed: 0x%x", eglGetError());
    }

    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    return result;
}
