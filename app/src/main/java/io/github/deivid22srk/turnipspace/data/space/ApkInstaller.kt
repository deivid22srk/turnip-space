package io.github.deivid22srk.turnipspace.data.space

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.domain.ApkInstallResult
import io.github.deivid22srk.turnipspace.domain.InstalledAppInfo
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Installs a user-provided APK into a virtual space:
 *  1. SAF stream → sandbox copy (2 GB hard cap).
 *  2. Metadata parsing via PackageManager.getPackageArchiveInfo (public API).
 *  3. Native lib extraction for the device's best ABI (so the plugin
 *     ClassLoader can resolve System.loadLibrary and our driver loader can
 *     act before the game's native code runs).
 */
class ApkInstaller(
    private val context: Context,
    private val repo: SpaceRepository,
) {

    companion object {
        private const val MAX_APK_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
    }

    suspend fun install(spaceId: String, apkUri: Uri): ApkInstallResult {
        val dest = repo.apkFile(spaceId)
        return try {
            // 1. Copy with progress + size cap.
            val total = querySize(apkUri)
            if (total > MAX_APK_BYTES) {
                return ApkInstallResult.Failure("error_apk_too_large", "size=$total")
            }
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(apkUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return ApkInstallResult.Failure("error_copy_failed", "content resolver returned null")

            // 2. Parse metadata.
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
            val info: PackageInfo? = pm.getPackageArchiveInfo(dest.absolutePath, flags)
            if (info == null || info.applicationInfo == null) {
                dest.delete()
                AppLogger.w("ApkInstaller", "getPackageArchiveInfo returned null for $apkUri")
                return ApkInstallResult.Failure("error_invalid_apk", null)
            }
            val appInfo = info.applicationInfo
            appInfo.sourceDir = dest.absolutePath
            appInfo.publicSourceDir = dest.absolutePath

            val label = try {
                appInfo.loadLabel(pm)?.toString() ?: info.packageName ?: "App"
            } catch (_: Exception) {
                info.packageName ?: "App"
            }

            val activities = info.activities?.map { it.name } ?: emptyList()
            val launcher = pickLauncherActivity(activities)

            // 3. Native libs for best device ABI.
            val abi = bestAbi(dest) ?: Build.SUPPORTED_ABIS.firstOrNull() ?: ""
            val extracted = extractNativeLibs(spaceId, dest, abi)

            val installed = InstalledAppInfo(
                packageName = appInfo.packageName ?: "",
                label = label,
                versionName = info.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong(),
                minSdk = appInfo.minSdkVersion,
                mainActivity = launcher,
                nativeLibraries = extracted,
                abi = abi,
                sha256 = sha256(dest),
                installedAt = System.currentTimeMillis(),
            )
            AppLogger.i(
                "ApkInstaller",
                "installed into space $spaceId: pkg=${installed.packageName} v=${installed.versionName} " +
                    "minSdk=${installed.minSdk} launcher=${installed.mainActivity} abi=$abi libs=${extracted.size}"
            )
            if (installed.minSdk > Build.VERSION.SDK_INT) {
                AppLogger.w("ApkInstaller", "plugin requires minSdk ${installed.minSdk} > device ${Build.VERSION.SDK_INT}; launch will likely fail")
            }
            ApkInstallResult.Success(installed)
        } catch (t: Throwable) {
            AppLogger.e("ApkInstaller", "install failed", t)
            ApkInstallResult.Failure("error_generic", t.message)
        }
    }

    /**
     * Launch-activity heuristic. NOTE (documented limitation):
     * PackageManager.getPackageArchiveInfo exposes ActivityInfo WITHOUT the
     * original intent filters (no categories), so the classic "LAUNCHER
     * category" scan is impossible for archive packages. Heuristics used:
     *  1. activity named *.MainActivity
     *  2. activity whose simple name mentions Launcher
     *  3. first declared activity (manifest order — correct for most games,
     *     whose engine activity is declared first).
     */
    private fun pickLauncherActivity(activities: List<String>): String? {
        if (activities.isEmpty()) return null
        return activities.firstOrNull { it.endsWith(".MainActivity") }
            ?: activities.firstOrNull { it.substringAfterLast('.').contains("Launcher", ignoreCase = true) }
            ?: activities.first()
    }

    private fun querySize(uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    private fun bestAbi(apk: File): String? = try {
        ZipFile(apk).use { zip ->
            for (abi in Build.SUPPORTED_ABIS) {
                val hasLib = zip.entries().asSequence().any {
                    !it.isDirectory && it.name.startsWith("lib/$abi/") && it.name.endsWith(".so")
                }
                if (hasLib) return abi
            }
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun extractNativeLibs(spaceId: String, apk: File, abi: String): List<String> {
        if (abi.isBlank()) return emptyList()
        val libDir = File(repo.nativeLibDir(spaceId), abi).apply { mkdirs() }
        val extracted = mutableListOf<String>()
        try {
            ZipFile(apk).use { zip ->
                val entries = zip.entries().asSequence().filter {
                    !it.isDirectory && it.name.startsWith("lib/$abi/") && it.name.endsWith(".so")
                }
                for (e in entries) {
                    val name = e.name.substringAfterLast('/')
                    val out = File(libDir, name)
                    zip.getInputStream(e).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    extracted.add(name)
                }
            }
        } catch (t: Throwable) {
            AppLogger.w("ApkInstaller", "native lib extraction partial failure: ${t.message}")
        }
        return extracted
    }

    private fun sha256(file: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                md.update(chunk, 0, read)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        ""
    }
}
