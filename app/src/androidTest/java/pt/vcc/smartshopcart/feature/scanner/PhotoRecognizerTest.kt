package pt.vcc.smartshopcart.feature.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.vcc.smartshopcart.core.ocr.PriceCandidateParser
import pt.vcc.smartshopcart.domain.model.PriceKind

/**
 * Valida a estratégia A (§5.1) no dispositivo: código de barras e preço lidos da mesma imagem.
 * A etiqueta é desenhada em memória porque a câmara do emulador não mostra etiquetas reais.
 */
@RunWith(AndroidJUnit4::class)
class PhotoRecognizerTest {

    @Test
    fun codigoEPrecoNaMesmaImagem() = runBlocking {
        val recognition = PhotoRecognizer().use { it.recognize(InputImage.fromBitmap(label("3017620422003"), 0)) }

        assertEquals(listOf("3017620422003"), recognition.barcodes)
        val prices = PriceCandidateParser.parse(recognition.lines)
        assertEquals("texto: ${prices.rawText}", 149L, prices.suggested?.cents)
        assertTrue("texto: ${prices.rawText}", prices.candidates.any { it.cents == 298L && it.kind == PriceKind.PER_KG })
    }

    @Test
    fun imagemSemCodigoNemPreco() = runBlocking {
        val blank = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val recognition = PhotoRecognizer().use { it.recognize(InputImage.fromBitmap(blank, 0)) }

        assertTrue(recognition.barcodes.isEmpty())
        assertTrue(PriceCandidateParser.parse(recognition.lines).candidates.isEmpty())
    }

    private fun label(ean13: String): Bitmap {
        val bitmap = Bitmap.createBitmap(1200, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        text.textSize = 140f
        canvas.drawText("PROMO 1,49 €", 80f, 220f, text)
        text.textSize = 60f
        canvas.drawText("2,98 €/kg", 80f, 330f, text)

        val bars = Paint().apply { color = Color.BLACK }
        val module = 6f
        val left = (1200 - 95 * module) / 2
        ean13Modules(ean13).forEachIndexed { index, black ->
            if (black) canvas.drawRect(left + index * module, 500f, left + (index + 1) * module, 850f, bars)
        }
        return bitmap
    }

    private fun ean13Modules(code: String): BooleanArray {
        val l = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
        val r = l.map { pattern -> pattern.map { if (it == '0') '1' else '0' }.joinToString("") }
        val g = r.map { it.reversed() }
        val parity = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")
        val digits = code.map { it - '0' }
        val sb = StringBuilder("101")
        for (i in 1..6) sb.append(if (parity[digits[0]][i - 1] == 'L') l[digits[i]] else g[digits[i]])
        sb.append("01010")
        for (i in 7..12) sb.append(r[digits[i]])
        sb.append("101")
        return BooleanArray(sb.length) { sb[it] == '1' }
    }
}
