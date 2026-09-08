package io.github.deivid22srk.turnipspace.data.gpu

import android.os.Build
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.domain.GpuFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Domain-facing GPU description. */
data class GpuInfo(
    val renderer: String?,
    val socModel: String?,
    val adrenoSeries: Int?,
    val family: GpuFamily,
    val turnipSupported: Boolean,
    val recommendedNote: String?,
)

/**
 * Multi-fallback Adreno detector:
 *  1. Native EGL pbuffer probe (GL_RENDERER) — most reliable.
 *  2. Build.SOC_MODEL (API 31+) mapped through a Qualcomm SoC → Adreno table.
 *  3. KGSL sysfs gpu_model (readable on several devices).
 *
 * Optional Shizuku enrichment is applied by the caller when available.
 */
class GpuDetector(private val probe: GpuProbeBridge = object : GpuProbeBridge {}) {

    /** Injectable surface so ViewModels can call this off the main thread. */
    interface GpuProbeBridge {
        fun renderer(): String? = GpuProbe.probeRenderer()
    }

    suspend fun detect(): GpuInfo = withContext(Dispatchers.Default) {
        val renderer = try {
            probe.renderer()
        } catch (t: Throwable) {
            AppLogger.w("GpuDetector", "probe failed: ${t.message}")
            null
        }
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null
        val series = adrenoSeriesFromRenderer(renderer)
            ?: adrenoSeriesFromSoc(soc)
            ?: adrenoSeriesFromKgsl()

        val family = when {
            series == null -> GpuFamily.UNKNOWN
            series >= 800 -> GpuFamily.ADRENO_8XX
            series >= 700 -> GpuFamily.ADRENO_7XX
            series >= 600 -> GpuFamily.ADRENO_6XX
            else -> GpuFamily.ADRENO_LEGACY
        }
        val supported = family == GpuFamily.ADRENO_6XX ||
            family == GpuFamily.ADRENO_7XX ||
            family == GpuFamily.ADRENO_8XX

        val note = when (family) {
            GpuFamily.ADRENO_6XX -> "Turnip a6xx support (recommended drivers: Turnip CI a6xx builds)"
            GpuFamily.ADRENO_7XX -> "Turnip a7xx support (recommended drivers: Turnip CI a7xx builds)"
            GpuFamily.ADRENO_8XX -> "Turnip a8xx support (bleeding edge — use recent CI builds)"
            GpuFamily.ADRENO_LEGACY -> "Adreno 5xx or older: Turnip does NOT support this GPU. Keep the proprietary driver."
            GpuFamily.UNKNOWN -> "GPU not detected. Driver compatibility cannot be suggested — import a driver matching your device."
        }
        GpuInfo(renderer, soc, series, family, supported, note)
            .also { AppLogger.i("GpuDetector", "detected: renderer=${it.renderer} soc=${it.socModel} series=${it.adrenoSeries} family=${it.family}") }
    }

    private fun adrenoSeriesFromRenderer(renderer: String?): Int? {
        val m = Regex("Adreno\\s*\\(TM\\)?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(renderer ?: return null)
        return m?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun adrenoSeriesFromSoc(socModel: String?): Int? {
        if (socModel.isNullOrBlank() || socModel == Build.UNKNOWN) return null
        // Qualcomm SoC → Adreno GPU mapping (main SoC families).
        val map = mapOf(
            "SM8750" to 830, "SM8650" to 750, "SM8550" to 740,
            "SM8475" to 730, "SM8450" to 730, "SDM845" to 630,
            "SM8250" to 650, "SM8150" to 640, "SM8350" to 660,
            "SM7325" to 6425, "SM7250" to 650, "SM7225" to 619,
            "SM7150" to 618, "SM6375" to 619, "SM6350" to 619,
            "SM6150" to 610, "SM6115" to 610, "SM4250" to 505,
        )
        map[socModel.uppercase()]?.let { return it }
        // Fallback: family prefix — SM8x => flagship line.
        val upper = socModel.uppercase()
        return when {
            upper.startsWith("SM87") || upper.startsWith("SM86") || upper.startsWith("SM85") -> 740
            upper.startsWith("SM84") -> 730
            upper.startsWith("SM83") || upper.startsWith("SM82") -> 650
            upper.startsWith("SM81") || upper.startsWith("SM71") || upper.startsWith("SM72") -> 640
            upper.startsWith("SM61") || upper.startsWith("SM63") || upper.startsWith("SM64") -> 610
            upper.startsWith("SDM8") -> 630
            else -> null
        }
    }

    private fun adrenoSeriesFromKgsl(): Int? {
        return try {
            val f = File("/sys/class/kgsl/kgsl-3d0/gpu_model")
            if (f.canRead()) {
                val m = Regex("(\\d{3,4})").find(f.readText().trim())
                m?.groupValues?.get(1)?.toIntOrNull()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
