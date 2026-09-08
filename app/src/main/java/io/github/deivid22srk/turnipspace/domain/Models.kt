package io.github.deivid22srk.turnipspace.domain

import io.github.deivid22srk.turnipspace.data.gpu.GpuInfo

/** Adreno GPU family buckets relevant for Turnip support. */
enum class GpuFamily { ADRENO_6XX, ADRENO_7XX, ADRENO_8XX, ADRENO_LEGACY, UNKNOWN }

/** An imported Turnip/AdrenoTools-compatible driver. */
data class DriverInfo(
    val id: String,
    val name: String,
    val author: String?,
    val description: String?,
    val version: String?,
    /** AdrenoTools driver API version declared in meta.json (e.g. 4, 5). */
    val apiVersion: Int?,
    val vendor: String?,
    val moduleId: String?,
    /** Extracted .so the loader must dlopen (e.g. libvulkan_freedreno.so). */
    val libraryFileName: String,
    /** Absolute path of the extracted driver .so inside the app sandbox. */
    val libraryPath: String,
    val sha256: String,
    val importedAt: Long,
)

/** The app installed inside a virtual space (never installed system-wide). */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val mainActivity: String?,
    val nativeLibraries: List<String>,
    val abi: String,
    val sha256: String,
    val installedAt: Long,
)

/** A managed virtual space that can host exactly one app. */
data class VirtualSpace(
    val id: String,
    val name: String,
    val createdAt: Long,
    val installedApp: InstalledAppInfo?,
    /** Driver selected for this space; null = use the system driver. */
    val selectedDriverId: String?,
)

/** Result of launching a space through the virtual engine. */
sealed interface LaunchResult {
    data class Success(val hookActive: Boolean, val driverPreloaded: Boolean) : LaunchResult
    data class Failure(val errorKey: String, val detail: String?) : LaunchResult
}

/** Result of importing a driver zip. */
sealed interface DriverImportResult {
    data class Success(val driver: DriverInfo) : DriverImportResult
    data class Failure(val errorKey: String, val detail: String?) : DriverImportResult
}

/** Result of installing an APK into a space. */
sealed class ApkInstallResult {
    data class Success(val app: InstalledAppInfo) : ApkInstallResult()
    data class Failure(val errorKey: String, val detail: String?) : ApkInstallResult()
}

/** GpuInfo lives in data.gpu (native bridge nearby) but is part of the domain surface. */
typealias GpuDetection = GpuInfo
