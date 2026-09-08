package io.github.deivid22srk.turnipspace

import android.app.Application
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.di.AppContainer
import io.github.deivid22srk.turnipspace.virtual.AppContextHolder

class TurnipSpaceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppContextHolder.init(this)
        container = AppContainer(this)
        installCrashHandler()
        AppLogger.i("App", "Turnip Space started (v${BuildConfig.VERSION_NAME})")
    }

    /**
     * Last-resort crash handler: writes the stack trace to the app log before
     * the process dies, so users can export diagnostics. No silent crashes.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.e(
                    "Crash",
                    "uncaught on thread ${thread.name}",
                    throwable,
                )
                AppLogger.e("Crash", "stacktrace:\n${throwable.stackTraceToString()}")
            } catch (_: Throwable) {
                // Never throw from the handler itself.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
