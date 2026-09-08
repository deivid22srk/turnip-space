package io.github.deivid22srk.turnipspace.virtual

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.loader.ResourcesProvider
import android.os.ParcelFileDescriptor
import dalvik.system.DexClassLoader
import io.github.deivid22srk.turnipspace.common.AppLogger
import java.io.File

/**
 * Loads a plugin APK (the app "installed" inside a virtual space) into the
 * host process:
 *
 *  - Code:     DexClassLoader over the space's base.apk, parented by the host
 *              class loader (host provides androidx/Compose runtime classes;
 *              plugin-provided classes take precedence for its own packages).
 *  - Resources: API 30+ uses the public ResourcesProvider/ResourcesLoader
 *              API. Older versions fall back to the classic reflection on
 *              AssetManager.addAssetPath — wrapped in try/catch, with a clear
 *              log line when unavailable (never crashes).
 *  - Native:   nativeLibraryDir points at the space's extracted lib dir so
 *              System.loadLibrary() inside the plugin resolves its own .so
 *              files. This is also the point where the Turnip driver has
 *              already been preloaded by VirtualEngine.
 */
class PluginApk(
    val apkPath: String,
    val nativeLibDir: String?,
    val optimizedDir: String,
) {

    val classLoader: ClassLoader by lazy {
        AppLogger.i(TAG, "creating DexClassLoader for $apkPath (libs=$nativeLibDir)")
        DexClassLoader(
            apkPath,
            optimizedDir,
            nativeLibDir,
            TurnipSpaceHostClasses::class.java.classLoader,
        )
    }

    val resources: Resources? by lazy { createResources() }

    fun loadPackageInfo(): PackageInfo? {
        val pm = AppContextHolder.get().packageManager
        return pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
            ?.also { it.applicationInfo?.sourceDir = apkPath }
    }

    private fun createResources(): Resources? {
        val context = AppContextHolder.get()
        val host = context.resources
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val pfd = ParcelFileDescriptor.open(File(apkPath), ParcelFileDescriptor.MODE_READ_ONLY)
                val provider = ResourcesProvider.loadFromApk(pfd, null)
                val loader = android.content.res.loader.ResourcesLoader()
                loader.addProvider(provider)
                val res = Resources(host.assets, host.displayMetrics, host.configuration)
                res.addLoaders(loader)
                AppLogger.i(TAG, "plugin resources via ResourcesProvider (API 30+)")
                res
            } catch (t: Throwable) {
                AppLogger.e(TAG, "ResourcesProvider path failed, trying addAssetPath", t)
                createResourcesLegacy()
            }
        } else {
            createResourcesLegacy()
        }
    }

    @Suppress("PrivateApi")
    private fun createResourcesLegacy(): Resources? = try {
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance() as AssetManager
        val addAssetPath = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPath.isAccessible = true
        val cookie = addAssetPath.invoke(assets, apkPath) as? Int
        if (cookie == null || cookie <= 0) {
            AppLogger.e(TAG, "addAssetPath returned $cookie for $apkPath")
            null
        } else {
            val host = AppContextHolder.get().resources
            AppLogger.i(TAG, "plugin resources via addAssetPath (legacy)")
            Resources(assets, host.displayMetrics, host.configuration)
        }
    } catch (t: Throwable) {
        AppLogger.e(TAG, "legacy resource loading failed (hidden API restrictions)", t)
        null
    }

    /** Marker used only to reference the host class loader cleanly. */
    private object TurnipSpaceHostClasses

    private companion object {
        const val TAG = "PluginApk"
    }
}

/** Application-context holder so virtual classes can access the app context. */
object AppContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context =
        appContext ?: error("AppContextHolder not initialised — TurnipSpaceApp must run first")
}

/** Shared runtime state for the virtual engine (process-scoped). */
object VirtualEngineRuntime {
    @Volatile
    var pluginApk: PluginApk? = null

    @Volatile
    var pendingLaunch: PendingLaunch? = null

    @Volatile
    var hookInstalled: Boolean = false

    data class PendingLaunch(
        val spaceId: String,
        val targetActivity: String,
    )
}
