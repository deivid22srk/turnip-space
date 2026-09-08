package io.github.deivid22srk.turnipspace.common

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Crash-safe ELF .dynsym reader used to validate driver .so files WITHOUT
 * dlopen'ing them. dlopen executes constructors of the loaded library — a
 * corrupted or malicious .so could segfault the app — so at import time we
 * parse the ELF structure in pure Kotlin and only check whether the Vulkan
 * ICD entrypoints are exported. The real dlopen happens later, exclusively
 * on explicit user launch (see NativeDriverLoader).
 */
object ElfSymbolParser {

    data class Result(
        val symbols: Set<String>,
        val isElf: Boolean,
        val architecture: String?,
        val parseError: String?,
    )

    fun parse(file: File): Result {
        return try {
            val bytes = file.readBytes()
            if (bytes.size < 64) return Result(emptySet(), false, null, "file too small")
            parseBytes(bytes)
        } catch (t: Throwable) {
            AppLogger.w("ElfParser", "parse failed: ${t.message}")
            Result(emptySet(), false, null, t.message)
        }
    }

    private fun parseBytes(bytes: ByteArray): Result {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // --- ELF header ---
        if (buf.get(0) != 0x7F.toByte() || buf.get(1) != 'E'.code.toByte()) {
            return Result(emptySet(), false, null, "not an ELF file")
        }
        val is64 = when (buf.get(4).toInt()) {
            1 -> false
            2 -> true
            else -> return Result(emptySet(), false, null, "unknown ELF class")
        }
        // e_machine at offset 18 (u16)
        val machine = shortAt(bytes, 18)
        val arch = when (machine) {
            40.toShort() -> "arm"
            183.toShort() -> "aarch64"
            3.toShort() -> "x86"
            62.toShort() -> "x86_64"
            else -> "machine-$machine"
        }

        val (shoff, shentsize, shnum) = if (is64) {
            Triple(longAt(bytes, 0x28), shortAt(bytes, 0x3A).toInt(), shortAt(bytes, 0x3C).toInt())
        } else {
            Triple(intAt(bytes, 0x20).toLong() and 0xFFFFFFFFL, shortAt(bytes, 0x2E).toInt(), shortAt(bytes, 0x30).toInt())
        }
        if (shoff <= 0 || shnum <= 0 || shentsize <= 0) {
            return Result(emptySet(), true, arch, "no section headers")
        }

        // --- Section headers ---
        data class Section(val type: Int, val offset: Long, val size: Long, val link: Int, val entsize: Long)
        val sections = ArrayList<Section>(shnum)
        for (i in 0 until shnum) {
            val base = (shoff + i.toLong() * shentsize).toInt()
            val type = if (is64) intAt(bytes, base + 4) else intAt(bytes, base + 4)
            val offset = if (is64) longAt(bytes, base + 0x18) else intAt(bytes, base + 0x10).toLong() and 0xFFFFFFFFL
            val size = if (is64) longAt(bytes, base + 0x20) else intAt(bytes, base + 0x14).toLong() and 0xFFFFFFFFL
            val link = if (is64) intAt(bytes, base + 0x28) else intAt(bytes, base + 0x18)
            val entsize = if (is64) longAt(bytes, base + 0x38) else intAt(bytes, base + 0x24).toLong() and 0xFFFFFFFFL
            sections.add(Section(type, offset, size, link, entsize))
        }

        val dynSym = sections.firstOrNull { it.type == 11 } // SHT_DYNSYM
            ?: return Result(emptySet(), true, arch, "no .dynsym section")
        val strTab = sections.getOrNull(dynSym.link)
            ?: return Result(emptySet(), true, arch, "no string table for .dynsym")

        val symCount = if (dynSym.entsize > 0) (dynSym.size / dynSym.entsize).toInt() else 0
        val names = HashSet<String>(symCount.coerceAtMost(4096))
        val nameSize = if (is64) 24 else 12 // st_name offset within symbol entry

        for (i in 0 until symCount) {
            val entry = dynSym.offset + i.toLong() * dynSym.entsize
            if (entry + nameSize > bytes.size) break
            val nameOff = intAt(bytes, entry.toInt())
            if (nameOff <= 0) continue
            val start = (strTab.offset + nameOff).toInt()
            if (start >= bytes.size || start < 0) continue
            var end = start
            while (end < bytes.size && bytes[end] != 0.toByte() && end - start < 256) end++
            if (end > start) {
                names.add(String(bytes, start, end - start, Charsets.UTF_8))
            }
        }
        return Result(names, true, arch, null)
    }

    /** True when the file exports every symbol in [required]. */
    fun hasAllSymbols(result: Result, required: List<String>): Boolean =
        result.symbols.containsAll(required)

    private fun shortAt(b: ByteArray, off: Int): Short =
        ((b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)).toShort()

    private fun intAt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun longAt(b: ByteArray, off: Int): Long =
        (intAt(b, off).toLong() and 0xFFFFFFFFL) or
            ((intAt(b, off + 4).toLong() and 0xFFFFFFFFL) shl 32)
}
