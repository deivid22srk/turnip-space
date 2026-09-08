package io.github.deivid22srk.turnipspace.data.driver

import android.content.Context
import android.net.Uri
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.domain.DriverInfo
import io.github.deivid22srk.turnipspace.domain.DriverImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Imports, stores and lists Turnip drivers. Storage layout:
 *
 *   files/drivers/index.json            — driver registry
 *   files/drivers/<id>/                 — extracted zip contents
 *   files/drivers/<id>/<lib>.so         — the driver library itself
 *
 * Everything stays inside the app sandbox (Scoped Storage compliant).
 */
class DriverRepository(private val context: Context) {

    private val driversDir: File get() = File(context.filesDir, "drivers").apply { mkdirs() }
    private val indexFile: File get() = File(driversDir, "index.json")

    suspend fun import(zipUri: Uri): DriverImportResult = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "driver-import-${System.currentTimeMillis()}.zip")
        try {
            // 1. Copy zip into sandbox (SAF source may be a cloud/stream provider).
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext DriverImportResult.Failure("error_copy_failed", "content resolver returned null")

            // 2. Parse meta.json + locate the ICD library (tolerant parser).
            val parsed = try {
                DriverZipParser.parse(temp)
            } catch (e: DriverParseException) {
                AppLogger.w("DriverRepo", "parse failed: ${e.message}")
                return@withContext DriverImportResult.Failure(e.errorKey, e.message)
            }

            // 3. Extract to a stable directory.
            val id = UUID.randomUUID().toString()
            val dest = File(driversDir, id).apply { mkdirs() }
            ZipFile(temp).use { zip ->
                for (entryName in parsed.entries) {
                    val entry = zip.getEntry(entryName) ?: continue
                    val out = File(dest, entryName)
                    if (!out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
                        // Zip-slip protection: reject entries escaping the dest dir.
                        return@withContext DriverImportResult.Failure(
                            "error_invalid_driver", "zip entry escapes target dir: $entryName"
                        )
                    }
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            // 4. Static ELF validation (crash-safe — no dlopen here).
            val soFile = findLibrary(dest, parsed.libraryFileName)
                ?: return@withContext DriverImportResult.Failure(
                    "error_no_vulkan_so", "extracted library not found: ${parsed.libraryFileName}"
                )
            val elfError = DriverZipParser.validateExtractedLibrary(soFile)
            if (elfError != null) {
                dest.deleteRecursively()
                AppLogger.w("DriverRepo", "ELF validation failed: $elfError")
                return@withContext DriverImportResult.Failure("error_invalid_driver", elfError)
            }

            val info = DriverInfo(
                id = id,
                name = parsed.name,
                author = parsed.author,
                description = parsed.description,
                version = parsed.version,
                apiVersion = parsed.apiVersion,
                vendor = parsed.vendor,
                moduleId = parsed.moduleId,
                libraryFileName = parsed.libraryFileName,
                libraryPath = soFile.absolutePath,
                sha256 = DriverZipParser.sha256(temp),
                importedAt = System.currentTimeMillis(),
            )

            // 5. Persist registry.
            val all = readIndex().toMutableList()
            all.removeAll { it.name == info.name && it.version == info.version }
            all.add(info)
            writeIndex(all)

            AppLogger.i("DriverRepo", "driver imported: ${info.name} v${info.version} api=${info.apiVersion} lib=${info.libraryFileName}")
            DriverImportResult.Success(info)
        } catch (t: Throwable) {
            AppLogger.e("DriverRepo", "import crashed", t)
            DriverImportResult.Failure("error_generic", t.message)
        } finally {
            temp.delete()
        }
    }

    fun list(): List<DriverInfo> = readIndex().sortedBy { it.name.lowercase() }

    fun get(id: String): DriverInfo? = readIndex().firstOrNull { it.id == id }

    fun delete(id: String) {
        File(driversDir, id).deleteRecursively()
        writeIndex(readIndex().filterNot { it.id == id })
        AppLogger.i("DriverRepo", "driver deleted: $id")
    }

    private fun findLibrary(dir: File, libName: String): File? {
        dir.walkTopDown().forEach { f ->
            if (f.isFile && f.name == libName) return f
        }
        return null
    }

    private fun readIndex(): List<DriverInfo> = try {
        if (!indexFile.exists()) emptyList()
        else {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    DriverInfo(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        author = o.optString("author").ifBlank { null },
                        description = o.optString("description").ifBlank { null },
                        version = o.optString("version").ifBlank { null },
                        apiVersion = o.optInt("apiVersion", -1).takeIf { it > 0 },
                        vendor = o.optString("vendor").ifBlank { null },
                        moduleId = o.optString("moduleId").ifBlank { null },
                        libraryFileName = o.getString("libraryFileName"),
                        libraryPath = o.getString("libraryPath"),
                        sha256 = o.getString("sha256"),
                        importedAt = o.getLong("importedAt"),
                    )
                }.getOrNull()
            }
        }
    } catch (t: Throwable) {
        AppLogger.e("DriverRepo", "index read failed", t)
        emptyList()
    }

    private fun writeIndex(list: List<DriverInfo>) = try {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("name", d.name)
                    .put("author", d.author ?: "")
                    .put("description", d.description ?: "")
                    .put("version", d.version ?: "")
                    .put("apiVersion", d.apiVersion ?: -1)
                    .put("vendor", d.vendor ?: "")
                    .put("moduleId", d.moduleId ?: "")
                    .put("libraryFileName", d.libraryFileName)
                    .put("libraryPath", d.libraryPath)
                    .put("sha256", d.sha256)
                    .put("importedAt", d.importedAt)
            )
        }
        indexFile.writeText(arr.toString(2))
    } catch (t: Throwable) {
        AppLogger.e("DriverRepo", "index write failed", t)
    }
}
