package pt.vcc.smartshopcart.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCropTest {

    // Vista 1000×1500 com moldura 800×300 ao centro; imagem direita 3000×4000 (escala 0,375 → visível 2667×4000).
    private val frame = FrameCrop(viewWidth = 1000, viewHeight = 1500, frameWidth = 800, frameHeight = 300)

    @Test
    fun `sem rotacao corta a moldura ao centro com folga`() {
        val rect = frame.sourceRect(3000, 4000, 0)
        assertEquals(PixelRect(113, 1480, 2886, 2520), rect)
    }

    @Test
    fun `com rotacao converte para as coordenadas do sensor`() {
        // Sensor em paisagem 4000×3000: a zona direita (113..2886 × 1480..2520) fica deitada.
        assertEquals(PixelRect(1480, 114, 2520, 2887), frame.sourceRect(4000, 3000, 90))
        assertEquals(PixelRect(1480, 113, 2520, 2886), frame.sourceRect(4000, 3000, 270))
        assertEquals(PixelRect(114, 1480, 2887, 2520), frame.sourceRect(3000, 4000, 180))
    }

    @Test
    fun `sem medidas usa a imagem inteira`() {
        assertEquals(PixelRect(0, 0, 640, 480), FrameCrop(0, 0, 0, 0).sourceRect(640, 480, 90))
    }
}
