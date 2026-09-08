package io.github.deivid22srk.turnipspace.common

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ring-buffer + file logger. Everything the app does that the user may need
 * to diagnose lands here; Settings → Export writes it out. Crash handler
 * also writes through this class before delegating to the default handler.
 */
object AppLogger {

    private const val TAG = "TurnipSpace"
    private const val MAX_BUFFER = 4000
    private const val MAX_FILE_BYTES = 1L shl 20 // 1 MB per file
    private const val MAX_FILES = 3

    private val buffer = ArrayDeque<String>(MAX_BUFFER)
    private val lock = Any()
    private var logDir: File? = null
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        logDir = dir
        rotateIfNeeded(dir)
    }

    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String, tr: Throwable? = null) =
        log("ERROR", tag, message + (tr?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))

    private fun log(level: String, tag: String, message: String) {
        when (level) {
            "INFO" -> Log.i(TAG, "[$tag] $message")
            "WARN" -> Log.w(TAG, "[$tag] $message")
            else -> Log.e(TAG, "[$tag] $message")
        }
        synchronized(lock) {
            // SimpleDateFormat is NOT thread-safe — format strictly inside the
            // lock (log() is called from main + IO/Default dispatchers).
            val line = "${timeFmt.format(Date())} $level [$tag] $message"
            buffer.addLast(line)
            while (buffer.size > MAX_BUFFER) buffer.removeFirst()
            logDir?.let { dir ->
                try {
                    val f = File(dir, "app.log")
                    if (f.length() > MAX_FILE_BYTES) rotateIfNeeded(dir)
                    f.appendText(line + "\n")
                } catch (_: Exception) {
                    // Logging must never crash the app.
                }
            }
        }
    }

    /** Snapshot of the current buffer, newest last. */
    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) {
        buffer.clear()
        logDir?.listFiles()?.forEach { it.delete() }
    }

    /** Reads the on-disk log (older entries beyond the in-memory buffer). */
    fun readFullLog(): String {
        val dir = logDir ?: return ""
        val f = File(dir, "app.log")
        return if (f.exists()) {
            synchronized(lock) { f.readText() + snapshot().joinToString("\n") }
        } else snapshot().joinToString("\n")
    }

    private fun rotateIfNeeded(dir: File) {
        val current = File(dir, "app.log")
        if (current.exists() && current.length() >= MAX_FILE_BYTES) {
            // app-<n>.log shift
            for (i in MAX_FILES - 1 downTo 1) {
                val src = File(dir, "app-$i.log")
                val dst = File(dir, "app-${i + 1}.log")
                if (src.exists()) {
                    if (i + 1 > MAX_FILES) src.delete() else src.renameTo(dst)
                }
            }
            current.renameTo(File(dir, "app-1.log"))
        }
    }
}
