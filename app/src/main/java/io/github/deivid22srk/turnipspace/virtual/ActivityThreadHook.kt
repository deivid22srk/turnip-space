package io.github.deivid22srk.turnipspace.virtual

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.IBinder
import io.github.deivid22srk.turnipspace.common.AppLogger

/**
 * Replaces ActivityThread.mInstrumentation (same technique as VirtualApp).
 *
 * When the system starts one of our StubActivity components (which ARE
 * installed in the host manifest — so ActivityManager accepts them), the
 * framework calls newActivity(stubClassLoader, "….StubActivity", intent).
 * We intercept that call and instantiate the PLUGIN activity from the plugin
 * ClassLoader instead. Lifecycle callbacks are then driven by the framework
 * on the returned instance.
 *
 * Everything else is inherited untouched, so host activities (MainActivity,
 * Settings…) keep working identically.
 *
 * Hidden-API notes: ActivityThread.currentActivityThread() and the
 * mInstrumentation field are non-SDK interfaces; on Android 9+ they are on
 * the greylist and remain reachable, but this can change in future Android
 * releases. Every reflective step is guarded and reported — if the hook
 * fails the engine degrades to the StubActivity diagnostic screen instead
 * of crashing (see ARCHITECTURE.md → Known limitations).
 */
object ActivityThreadHook {

    private const val TAG = "ActivityThreadHook"

    fun install(): Boolean {
        if (VirtualEngineRuntime.hookInstalled) return true
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val thread = atClass
                .getDeclaredMethod("currentActivityThread")
                .invoke(null) ?: run {
                AppLogger.w(TAG, "ActivityThread.currentActivityThread() returned null (app not on main thread?)")
                return false
            }
            val field = atClass.getDeclaredField("mInstrumentation")
            field.isAccessible = true
            if (field.get(thread) is PluginInstrumentation) {
                VirtualEngineRuntime.hookInstalled = true
                return true
            }
            field.set(thread, PluginInstrumentation())
            VirtualEngineRuntime.hookInstalled = true
            AppLogger.i(TAG, "ActivityThread.mInstrumentation hooked successfully")
            true
        } catch (t: Throwable) {
            AppLogger.e(TAG, "hook failed — degraded launch mode will be used", t)
            false
        }
    }
}

class PluginInstrumentation : Instrumentation() {

    override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
        val pending = VirtualEngineRuntime.pendingLaunch
        val plugin = VirtualEngineRuntime.pluginApk
        val isStub = className != null &&
            className.startsWith("io.github.deivid22srk.turnipspace.virtual.StubActivity")
        if (isStub && pending != null && plugin != null) {
            try {
                val target = intent?.getStringExtra(VirtualEngine.EXTRA_TARGET_ACTIVITY)
                    ?: pending.targetActivity
                VirtualEngineRuntime.pendingLaunch = null
                AppLogger.i(TAG, "swapping stub '$className' -> plugin activity '$target'")
                return super.newActivity(plugin.classLoader, target, intent)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "plugin activity instantiation failed: $className", t)
                VirtualEngineRuntime.pendingLaunch = null
                // Fall through: instantiate the real stub, which shows a
                // diagnostic screen instead of crashing the process.
            }
        }
        if (cl == null || className == null) {
            AppLogger.e(TAG, "newActivity called with null cl/className; cannot proceed")
            error("Instrumentation.newActivity requires non-null classLoader and className")
        }
        return super.newActivity(cl, className, intent)
    }

    // Kept to satisfy older call paths (Activity.onCreate instrumentation).
    override fun newActivity(
        clazz: Class<*>?,
        context: android.content.Context?,
        token: IBinder?,
        application: android.app.Application?,
        intent: Intent?,
        info: android.content.pm.ActivityInfo?,
        title: CharSequence?,
        parent: Activity?,
        id: String?,
        lastNonConfigurationInstance: Any?,
    ): Activity {
        if (clazz == null || context == null) {
            AppLogger.e(TAG, "newActivity(clazz) called with null clazz/context")
            error("Instrumentation.newActivity(clazz) requires non-null clazz and context")
        }
        return super.newActivity(
            clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance
        )
    }
}
