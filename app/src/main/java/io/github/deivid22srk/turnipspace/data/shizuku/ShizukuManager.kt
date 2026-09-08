package io.github.deivid22srk.turnipspace.data.shizuku

import android.content.Context
import android.content.pm.PackageManager
import io.github.deivid22srk.turnipspace.common.AppLogger
import rikka.shizuku.Shizuku

/**
 * Optional Shizuku integration. Design contract: the app MUST work with
 * Shizuku missing, not installed, not running, or permission denied — every
 * capability behind this manager degrades to "reduced diagnostics" and the
 * rest of the app is unaffected.
 */
class ShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "Shizuku"
        const val REQUEST_CODE = 4217
    }

    /** Invoked on the Shizuku permission request callback (any thread). */
    @Volatile
    var onPermissionResult: ((granted: Boolean) -> Unit)? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        AppLogger.i(TAG, "permission result: requestCode=$requestCode granted=${grantResult == PackageManager.PERMISSION_GRANTED}")
        onPermissionResult?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "listener registration failed: ${t.message}")
        }
    }

    enum class State { AVAILABLE, PERMISSION_GRANTED, NOT_INSTALLED, NOT_RUNNING }

    fun state(): State {
        return try {
            val installed = try {
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (!installed) return State.NOT_INSTALLED
            if (!Shizuku.pingBinder()) return State.NOT_RUNNING
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                State.PERMISSION_GRANTED
            } else {
                State.AVAILABLE
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "state() degraded: ${t.message}")
            State.NOT_RUNNING
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "requestPermission failed: ${t.message}")
        }
    }

    /**
     * Runs a privileged shell command (e.g. `dumpsys gpu`) through Shizuku.
     * Returns null whenever Shizuku is unavailable — callers must handle it.
     * stderr is drained on a separate thread to avoid pipe-full deadlocks.
     */
    fun runCommand(command: String): String? {
        return try {
            if (state() != State.PERMISSION_GRANTED) return null
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stderrDrain = Thread {
                try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                }
            }
            stderrDrain.isDaemon = true
            stderrDrain.start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            stderrDrain.join(2000)
            out.take(64 * 1024)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "runCommand failed: ${t.message}")
            null
        }
    }
}
