package pt.vcc.smartshopcart.domain.model

import kotlinx.serialization.Serializable

/** Classificação de um preço lido por OCR (D8). */
enum class PriceKind { SALE, OLD, PER_KG, PER_L, DISCOUNT, UNKNOWN }

@Serializable
data class PriceCandidate(
    val cents: Long,
    val kind: PriceKind,
    val confidence: Float,
    val rawText: String,
) : java.io.Serializable

/** Registo de uma leitura OCR confirmada (o texto e os candidatos; nunca a fotografia). */
data class PriceCapture(
    val id: String,
    val sessionItemId: String?,
    val rawText: String,
    val candidates: List<PriceCandidate>,
    val detectedPriceCents: Long?,
    val confirmedPriceCents: Long?,
    val status: CaptureStatus,
    val capturedAt: Long,
)
