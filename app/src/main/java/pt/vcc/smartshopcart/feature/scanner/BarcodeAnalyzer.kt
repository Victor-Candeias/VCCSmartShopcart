package pt.vcc.smartshopcart.feature.scanner

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Analisa frames da câmara com o ML Kit (modelo incluído na app, D3) e devolve os valores distintos lidos.
 * Só EAN-8, EAN-13 e UPC-A (D21). Analisa no máximo um frame a cada [minIntervalMs].
 */
class BarcodeAnalyzer(
    private val minIntervalMs: Long = 300,
    private val onCodes: (List<String>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A)
            .build(),
    )
    private var lastAnalyzedAt = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val mediaImage = image.image
        if (mediaImage == null || now - lastAnalyzedAt < minIntervalMs) {
            image.close()
            return
        }
        lastAnalyzedAt = now
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val values = barcodes.mapNotNull { it.rawValue }.distinct()
                if (values.isNotEmpty()) onCodes(values)
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() = scanner.close()
}

/** Ignora o mesmo código lido de novo dentro da janela (D22). */
class ScanDebouncer(private val windowMs: Long = 3_000) {
    private var lastCode: String? = null
    private var lastAt = Long.MIN_VALUE

    fun accept(code: String, nowMs: Long): Boolean {
        if (code == lastCode && nowMs - lastAt < windowMs) return false
        mark(code, nowMs)
        return true
    }

    fun mark(code: String, nowMs: Long) {
        lastCode = code
        lastAt = nowMs
    }
}
