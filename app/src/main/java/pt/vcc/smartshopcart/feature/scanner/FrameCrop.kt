package pt.vcc.smartshopcart.feature.scanner

import kotlin.math.max
import kotlin.math.roundToInt

/** Retângulo em píxeis (`right`/`bottom` exclusivos). */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Moldura de enquadramento sobre a pré-visualização (`PreviewView` em `FILL_CENTER`), em píxeis do ecrã.
 * Serve para ler só o texto dentro da moldura (com [MARGIN] de folga), em vez da fotografia inteira.
 */
data class FrameCrop(val viewWidth: Int, val viewHeight: Int, val frameWidth: Int, val frameHeight: Int) {

    /**
     * Zona a ler na imagem da câmara ainda por rodar ([sourceWidth]×[sourceHeight], a rodar [rotationDegrees]
     * no sentido dos ponteiros do relógio para ficar direita).
     */
    fun sourceRect(sourceWidth: Int, sourceHeight: Int, rotationDegrees: Int): PixelRect {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val swap = rotation == 90 || rotation == 270
        val uprightW = if (swap) sourceHeight else sourceWidth
        val uprightH = if (swap) sourceWidth else sourceHeight
        val full = PixelRect(0, 0, sourceWidth, sourceHeight)
        if (viewWidth <= 0 || viewHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return full

        // FILL_CENTER: a imagem é ampliada até cobrir a vista e cortada ao centro.
        val scale = max(viewWidth.toFloat() / uprightW, viewHeight.toFloat() / uprightH)
        val cropW = (frameWidth * (1 + MARGIN) / scale).coerceAtMost(uprightW.toFloat())
        val cropH = (frameHeight * (1 + MARGIN) / scale).coerceAtMost(uprightH.toFloat())
        val l = ((uprightW - cropW) / 2).roundToInt()
        val t = ((uprightH - cropH) / 2).roundToInt()
        val r = (l + cropW.roundToInt()).coerceAtMost(uprightW)
        val b = (t + cropH.roundToInt()).coerceAtMost(uprightH)

        return when (rotation) {
            90 -> PixelRect(t, sourceHeight - r, b, sourceHeight - l)
            180 -> PixelRect(sourceWidth - r, sourceHeight - b, sourceWidth - l, sourceHeight - t)
            270 -> PixelRect(sourceWidth - b, l, sourceWidth - t, r)
            else -> PixelRect(l, t, r, b)
        }
    }

    companion object {
        /** Folga à volta da moldura (a pré-visualização e a fotografia podem não coincidir ao píxel). */
        const val MARGIN = 0.3f
    }
}
