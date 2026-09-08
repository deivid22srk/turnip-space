package io.github.deivid22srk.turnipspace.virtual

import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.data.driver.DriverRepository
import io.github.deivid22srk.turnipspace.data.driver.NativeDriverLoader
import io.github.deivid22srk.turnipspace.data.space.SpaceRepository
import io.github.deivid22srk.turnipspace.domain.LaunchResult
import io.github.deivid22srk.turnipspace.domain.VirtualSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates a space launch:
 *
 *   1. Validate the space has an installed APK.
 *   2. Load the plugin APK (DexClassLoader + Resources) into this process.
 *   3. Preload the selected Turnip driver through the native bridge
 *      (dlopen of libvulkan_freedreno.so from the app sandbox).
 *   4. Install the Instrumentation hook that swaps StubActivity → plugin
 *      activity at creation time.
 *   5. Start the stub component; the framework instantiates the plugin
 *      activity inside our process.
 *
 * Honest behaviour on failure: every step is guarded and returns a typed
 * LaunchResult the UI maps to a clear message. The process never dies from
 * a malformed APK or a broken driver (drivers are statically validated at
 * import; the runtime dlopen result is reported, never swallowed).
 */
class VirtualEngine(
    private val spaceRepo: SpaceRepository,
    private val driverRepo: DriverRepository,
) {

    companion object {
        const val EXTRA_SPACE_ID = "turnipspace.space_id"
        const val EXTRA_TARGET_ACTIVITY = "turnipspace.target_activity"
        private const val TAG = "VirtualEngine"
    }

    suspend fun launch(context: Context, space: VirtualSpace): LaunchResult = withContext(Dispatchers.IO) {
        val app = space.installedApp
            ?: return@withContext LaunchResult.Failure("error_no_app", "space ${space.id} has no APK")

        if (app.mainActivity == null) {
            return@withContext LaunchResult.Failure("error_no_launcher_activity", app.packageName)
        }
        if (app.minSdk > Build.VERSION.SDK_INT) {
            AppLogger.w(TAG, "plugin minSdk ${app.minSdk} > device ${Build.VERSION.SDK_INT}")
        }

        // --- 2. plugin load -------------------------------------------------
        val plugin = try {
            loadPlugin(space)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "plugin load failed", t)
            return@withContext LaunchResult.Failure("error_plugin_load_failed", t.message)
        }

        // --- 3. driver preload ----------------------------------------------
        var driverPreloaded = false
        var driverWarning: String? = null
        val driver = space.selectedDriverId?.let { driverRepo.get(it) }
        if (driver != null) {
            val err = arrayOf<String?>(null)
            val rc = try {
                NativeDriverLoader.openDriver(driver.libraryPath, err)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "native driver bridge threw", t)
                err[0] = t.message
                1
            }
            driverPreloaded = rc == 0
            if (!driverPreloaded) {
                driverWarning = err[0] ?: "unknown dlopen failure"
                AppLogger.e(TAG, "driver preload failed: $driverWarning — continuing with system Vulkan")
            } else {
                AppLogger.i(TAG, "Turnip driver preloaded: ${driver.libraryPath}")
            }
        }

        // --- 4. instrumentation hook ----------------------------------------
        val hookOk = ActivityThreadHook.install()

        // --- 5. stub launch ---------------------------------------------------
        VirtualEngineRuntime.pluginApk = plugin
        VirtualEngineRuntime.pendingLaunch =
            VirtualEngineRuntime.PendingLaunch(space.id, app.mainActivity)

        val stubClass = pickStubClass(space.id)
        val intent = Intent(context, stubClass).apply {
            putExtra(EXTRA_SPACE_ID, space.id)
            putExtra(EXTRA_TARGET_ACTIVITY, app.mainActivity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            AppLogger.i(TAG, "launch dispatched: space=${space.id} target=${app.mainActivity} hook=$hookOk driver=$driverPreloaded")
            LaunchResult.Success(hookActive = hookOk, driverPreloaded = driverPreloaded)
        } catch (t: Throwable) {
            VirtualEngineRuntime.pendingLaunch = null
            AppLogger.e(TAG, "startActivity failed", t)
            LaunchResult.Failure("error_launch_failed", t.message)
        }
    }

    /** Loads (or reuses) the plugin APK for a space. */
    private fun loadPlugin(space: VirtualSpace): PluginApk {
        val cached = VirtualEngineRuntime.pluginApk
        val apkPath = spaceRepo.apkFile(space.id).absolutePath
        if (cached != null && cached.apkPath == apkPath) return cached
        val nativeDir = spaceRepo.nativeLibDir(space.id)
            .resolve(space.installedApp?.abi ?: "")
            .takeIf { it.isDirectory }
        val plugin = PluginApk(
            apkPath = apkPath,
            nativeLibDir = nativeDir?.absolutePath,
            optimizedDir = spaceRepo.optimizedDir(space.id).absolutePath,
        )
        // Force class loader construction now so failures happen before the
        // stub is dispatched (typed error, no crash inside the framework).
        plugin.classLoader
        plugin.resources // best-effort; null tolerated
        return plugin
    }

    private fun pickStubClass(spaceId: String): Class<*> = when (kotlin.math.abs(spaceId.hashCode()) % 5) {
        0 -> StubActivity::class.java
        1 -> StubActivity1::class.java
        2 -> StubActivity2::class.java
        3 -> StubActivity3::class.java
        else -> StubActivity4::class.java
    }
}
