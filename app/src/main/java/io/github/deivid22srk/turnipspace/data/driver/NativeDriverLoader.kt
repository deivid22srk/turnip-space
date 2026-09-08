package io.github.deivid22srk.turnipspace.data.driver

/**
 * JNI bridge to the native driver loader (turnip_loader.cpp).
 *
 * IMPORTANT SAFETY MODEL:
 *  - Import time: zip + ELF validation happens in pure Kotlin (crash-safe).
 *  - Launch time: openDriver() performs the real dlopen. A corrupted .so
 *    that somehow passed static validation could still crash the process at
 *    dlopen; this is documented and accepted because the user explicitly
 *    launches the space at that moment, and the app logs the outcome.
 */
object NativeDriverLoader {

    init {
        System.loadLibrary("turniploader")
    }

    /**
     * dlopens [driverPath] with RTLD_NOW|RTLD_LOCAL, validates the Vulkan ICD
     * entrypoints and keeps the handle open for the process lifetime (same
     * policy as AdrenoTools host apps — never dlclose a registered ICD).
     *
     * @return 0 on success; 1 on failure with [outError] filled (index 0).
     */
    @JvmStatic
    external fun openDriver(driverPath: String, outError: Array<String?>): Int

    /** Lazy-load check used right before launching a space. */
    @JvmStatic
    external fun canOpenDriver(driverPath: String): Boolean
}
