package io.github.deivid22srk.turnipspace.data.space

import android.content.Context
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.domain.DriverInfo
import io.github.deivid22srk.turnipspace.domain.InstalledAppInfo
import io.github.deivid22srk.turnipspace.domain.VirtualSpace
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Registry of virtual spaces. Storage layout:
 *
 *   files/spaces/index.json          — space registry
 *   files/spaces/<id>/app/base.apk   — the user-provided APK (verbatim copy)
 *   files/spaces/<id>/lib/<abi>/*.so — extracted native libraries
 *   files/spaces/<id>/cache/optimized — DexClassLoader odex output dir
 */
class SpaceRepository(private val context: Context) {

    private val spacesDir: File get() = File(context.filesDir, "spaces").apply { mkdirs() }
    private val indexFile: File get() = File(spacesDir, "index.json")

    fun create(name: String): VirtualSpace {
        val space = VirtualSpace(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Space" },
            createdAt = System.currentTimeMillis(),
            installedApp = null,
            selectedDriverId = null,
        )
        writeIndex(readIndex() + space)
        File(spaceDir(space.id), "app").mkdirs()
        AppLogger.i("SpaceRepo", "space created: ${space.id} (${space.name})")
        return space
    }

    fun list(): List<VirtualSpace> = readIndex().sortedBy { it.createdAt }

    fun get(id: String): VirtualSpace? = readIndex().firstOrNull { it.id == id }

    fun update(space: VirtualSpace) {
        writeIndex(readIndex().map { if (it.id == space.id) space else it })
    }

    fun delete(id: String) {
        File(spacesDir, id).deleteRecursively()
        writeIndex(readIndex().filterNot { it.id == id })
        AppLogger.i("SpaceRepo", "space deleted: $id")
    }

    fun spaceDir(id: String): File = File(spacesDir, id).apply { mkdirs() }

    fun apkFile(id: String): File = File(spaceDir(id), "app/base.apk")

    fun optimizedDir(id: String): File = File(spaceDir(id), "cache/optimized").apply { mkdirs() }

    fun nativeLibDir(id: String): File = File(spaceDir(id), "lib").apply { mkdirs() }

    fun selectedDriver(space: VirtualSpace, drivers: List<DriverInfo>): DriverInfo? {
        val sid = space.selectedDriverId ?: return null
        return drivers.firstOrNull { it.id == sid }
    }

    // ---- persistence -----------------------------------------------------

    private fun readIndex(): List<VirtualSpace> = try {
        if (!indexFile.exists()) emptyList()
        else {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    val app = o.optJSONObject("installedApp")?.let { a ->
                        InstalledAppInfo(
                            packageName = a.getString("packageName"),
                            label = a.getString("label"),
                            versionName = a.optString("versionName").ifBlank { null },
                            versionCode = a.getLong("versionCode"),
                            minSdk = a.optInt("minSdk", 0),
                            mainActivity = a.optString("mainActivity").ifBlank { null },
                            nativeLibraries = a.optJSONArray("nativeLibraries")?.let { la ->
                                (0 until la.length()).map { la.getString(it) }
                            } ?: emptyList(),
                            abi = a.optString("abi", ""),
                            sha256 = a.optString("sha256", ""),
                            installedAt = a.optLong("installedAt", 0L),
                        )
                    }
                    VirtualSpace(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        createdAt = o.getLong("createdAt"),
                        installedApp = app,
                        selectedDriverId = o.optString("selectedDriverId").ifBlank { null },
                    )
                }.getOrNull()
            }
        }
    } catch (t: Throwable) {
        AppLogger.e("SpaceRepo", "index read failed", t)
        emptyList()
    }

    private fun writeIndex(list: List<VirtualSpace>) = try {
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
                .put("id", s.id)
                .put("name", s.name)
                .put("createdAt", s.createdAt)
                .put("selectedDriverId", s.selectedDriverId ?: "")
            s.installedApp?.let { a ->
                val libs = JSONArray()
                a.nativeLibraries.forEach { libs.put(it) }
                o.put(
                    "installedApp",
                    JSONObject()
                        .put("packageName", a.packageName)
                        .put("label", a.label)
                        .put("versionName", a.versionName ?: "")
                        .put("versionCode", a.versionCode)
                        .put("minSdk", a.minSdk)
                        .put("mainActivity", a.mainActivity ?: "")
                        .put("nativeLibraries", libs)
                        .put("abi", a.abi)
                        .put("sha256", a.sha256)
                        .put("installedAt", a.installedAt)
                )
            }
            arr.put(o)
        }
        indexFile.writeText(arr.toString(2))
    } catch (t: Throwable) {
        AppLogger.e("SpaceRepo", "index write failed", t)
    }
}
