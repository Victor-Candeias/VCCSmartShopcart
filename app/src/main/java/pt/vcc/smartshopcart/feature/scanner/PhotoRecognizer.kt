package pt.vcc.smartshopcart.feature.scanner

import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import pt.vcc.smartshopcart.core.ocr.OcrLine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Resultado de uma fotografia: códigos e linhas de texto lidos da mesma imagem. */
data class CaptureRecognition(val barcodes: List<String>, val lines: List<OcrLine>)

/**
 * Reconhece código de barras e texto na mesma imagem (§5.1 estratégia A), no dispositivo (D3).
 * A imagem fica só em memória e é libertada no fim (D8).
 */
class PhotoRecognizer : AutoCloseable {
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A)
            .build(),
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(input: InputImage): CaptureRecognition {
        val barcodes = barcodeScanner.process(input).await().mapNotNull { it.rawValue }.distinct()
        val text = textRecognizer.process(input).await()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.map { line -> OcrLine(line.text, line.boundingBox?.height() ?: 0) }
        }
        return CaptureRecognition(barcodes, lines)
    }

    suspend fun recognize(image: ImageProxy): CaptureRecognition = try {
        recognize(InputImage.fromBitmap(image.toBitmap(), image.imageInfo.rotationDegrees))
    } finally {
        image.close()
    }

    override fun close() {
        barcodeScanner.close()
        textRecognizer.close()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
