package io.github.deivid22srk.turnipspace.data.driver

import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.common.ElfSymbolParser
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Parser for AdrenoTools-format driver zips (K11MCH1/AdrenoToolsDrivers,
 * whitebelyash/freedreno_turnip-CI, The412Banner/Banners-Turnip).
 *
 * Zip layout (root level):
 *   meta.json
 *   libvulkan_freedreno.so (+ additional libs / subdirectories)
 *
 * meta.json is tolerant-parsed; the AdrenoTools parser itself accepts:
 *   { "name", "author", "package_name", "description", "api", "library",
 *     "vendor", "module_id", "version"? }
 * Older/other distributions omit some fields — we fall back to scanning the
 * zip for libvulkan*.so when the "library" key is absent.
 */
object DriverZipParser {

    /** Strict requirements from the AdrenoTools ICD contract. */
    private val REQUIRED_ICD_SYMBOLS = listOf(
        "vk_icdGetInstanceProcAddr",
        "vk_icdNegotiateLoaderICDInterfaceVersion",
    )

    data class Parsed(
        val name: String,
        val author: String?,
        val description: String?,
        val version: String?,
        val apiVersion: Int?,
        val vendor: String?,
        val moduleId: String?,
        val libraryFileName: String,
        /** Entry names inside the zip that must be extracted. */
        val entries: List<String>,
    )

    fun parse(zipFile: File): Parsed {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val entryNames = entries.filter { !it.isDirectory }.map { it.name }

            val metaEntry = entries.firstOrNull {
                !it.isDirectory && (it.name == "meta.json" || it.name.endsWith("/meta.json"))
            } ?: throw DriverParseException("error_invalid_driver", "meta.json not found in zip")

            val metaText = zip.getInputStream(metaEntry).bufferedReader().use { it.readText() }
            val meta = try {
                JSONObject(metaText)
            } catch (t: Throwable) {
                throw DriverParseException("error_invalid_driver", "meta.json is not valid JSON: ${t.message}")
            }

            val name = meta.optString("name", "").ifBlank {
                meta.optString("package_name", "driver")
            }

            val declaredLib = meta.optString("library", "").ifBlank { null }
            val libEntryName = when {
                declaredLib != null -> entryNames.firstOrNull {
                    it.substringAfterLast('/') == declaredLib
                }
                else -> null
            } ?: entryNames.firstOrNull {
                val simple = it.substringAfterLast('/')
                simple.startsWith("libvulkan") && simple.endsWith(".so")
            } ?: throw DriverParseException("error_no_vulkan_so", "no libvulkan*.so entry in zip")

            val libName = libEntryName.substringAfterLast('/')

            return Parsed(
                name = name,
                author = meta.optString("author", "").ifBlank { null },
                description = meta.optString("description", "").ifBlank { null },
                version = meta.optString("version", "").ifBlank { null },
                apiVersion = if (meta.has("api")) meta.optInt("api", -1).takeIf { it > 0 } else null,
                vendor = meta.optString("vendor", "").ifBlank { null },
                moduleId = meta.optString("module_id", "").ifBlank { null },
                libraryFileName = libName,
                entries = entryNames,
            )
        }
    }

    /**
     * Crash-safe validation of an extracted .so: static ELF symbol check.
     * Returns null when OK, otherwise a human-readable reason.
     */
    fun validateExtractedLibrary(soFile: File): String? {
        if (!soFile.exists() || soFile.length() == 0L) return "library file is empty"
        val elf = ElfSymbolParser.parse(soFile)
        if (!elf.isElf) return "library is not an ELF object (${elf.parseError})"
        AppLogger.i("DriverZipParser", "ELF arch=${elf.architecture} dynsyms=${elf.symbols.size}")
        if (elf.symbols.isEmpty() && elf.parseError != null) return "could not read ELF symbols: ${elf.parseError}"
        val missing = REQUIRED_ICD_SYMBOLS.filter { it !in elf.symbols }
        return if (missing.isEmpty()) null else "missing ICD symbols: $missing"
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                md.update(chunk, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

class DriverParseException(val errorKey: String, detail: String) : Exception(detail)
