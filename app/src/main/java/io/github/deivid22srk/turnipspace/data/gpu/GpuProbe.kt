package io.github.deivid22srk.turnipspace.data.gpu

/**
 * JNI bridge to the EGL pbuffer GL_RENDERER probe implemented in gpu_probe.cpp.
 */
object GpuProbe {

    init {
        System.loadLibrary("turniploader")
    }

    /**
     * Returns the GL_RENDERER string (e.g. "Adreno (TM) 740") or null when
     * no GPU context could be created (emulators, headless devices, etc).
     */
    @JvmStatic
    external fun nativeProbeRenderer(): String?

    fun probeRenderer(): String? = try {
        nativeProbeRenderer()
    } catch (t: Throwable) {
        io.github.deivid22srk.turnipspace.common.AppLogger.w(
            "GpuProbe", "native probe threw: ${t.message}"
        )
        null
    }
}
