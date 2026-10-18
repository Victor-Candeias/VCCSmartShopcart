package pt.vcc.smartshopcart.feature.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    /** Com [readBarcodes] a `false` só lê texto (fotografia do preço ou do nome). */
    suspend fun recognize(input: InputImage, readBarcodes: Boolean = true): CaptureRecognition {
        val barcodes = if (readBarcodes) {
            barcodeScanner.process(input).await().mapNotNull { it.rawValue }.distinct()
        } else {
            emptyList()
        }
        val text = textRecognizer.process(input).await()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val box = line.boundingBox
                OcrLine(line.text, box?.height() ?: 0, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0)
            }
        }
        return CaptureRecognition(barcodes, lines)
    }

    /** Com [frame] lê só a zona da moldura de enquadramento (com folga), já rodada para ficar direita. */
    suspend fun recognize(image: ImageProxy, readBarcodes: Boolean = true, frame: FrameCrop? = null): CaptureRecognition {
        val rotation = image.imageInfo.rotationDegrees
        val input = withContext(Dispatchers.Default) {
            try {
                val bitmap = image.toBitmap()
                if (frame == null) {
                    InputImage.fromBitmap(bitmap, rotation)
                } else {
                    val rect = frame.sourceRect(bitmap.width, bitmap.height, rotation)
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    val cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height, matrix, true)
                    if (cropped !== bitmap) bitmap.recycle()
                    InputImage.fromBitmap(cropped, 0)
                }
            } finally {
                image.close()
            }
        }
        return recognize(input, readBarcodes)
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
