package pt.vcc.smartshopcart.core.ocr

import pt.vcc.smartshopcart.domain.model.PriceCandidate
import pt.vcc.smartshopcart.domain.model.PriceKind
import kotlin.math.roundToInt

/** Linha reconhecida pelo OCR; [height] é a altura da caixa em píxeis (0 se desconhecida). */
data class OcrLine(val text: String, val height: Int = 0)

data class PriceParseResult(
    val candidates: List<PriceCandidate>,
    /** Candidato destacado como sugestão; nunca é aceite sem confirmação do utilizador. */
    val suggested: PriceCandidate?,
    val rawText: String,
)

/**
 * Extrai candidatos a preço de texto de etiquetas portuguesas (§5.3, D26).
 *
 * Formatos: `1,49`, `1.49`, `1,49 €`, `€ 1,49`, `1€49` e `1 49` (cêntimos separados/expoente, só junto a `€`
 * ou sozinho na linha). Marcadores: `€/kg`, `€/l`, `antes`, `promo`, descontos e percentagens.
 * Não escolhe o maior, o menor nem o primeiro número: ordena por tipo, contexto e tamanho do texto.
 */
object PriceCandidateParser {

    const val MIN_SUGGESTION_CONFIDENCE = 0.45f

    private val numberPattern = Regex(
        "(?<![\\d,.])(\\d{1,4})\\s?€\\s?(\\d{2})(?!\\d)(?![,.]\\d)" + // 1€49
            "|(?<![\\d,.])(\\d{1,4})[,.](\\d{2})(?!\\d)(?![,.]\\d)" + // 1,49 / 1.49
            "|(?<![\\d,.])(\\d{1,3})\\s(\\d{2})(?![\\d,.])", // 1 49
    )
    private val perKgAfter = Regex("^\\s*(€|eur)?\\s*(/|por)\\s*kg\\b")
    private val perKgBefore = Regex("(/|por)\\s*kg\\s*:?\\s*€?\\s*$")
    private val perLAfter = Regex("^\\s*(€|eur)?\\s*(/|por)\\s*(l|lt|litro)\\b")
    private val perLBefore = Regex("(/|por)\\s*(l|lt|litro)\\s*:?\\s*€?\\s*$")
    private val oldWords = Regex("\\b(antes|anterior|pvp antigo|era)\\b")
    private val promoWords = Regex("promo|oferta|\\bagora\\b|campanha")
    private val discountWords = Regex("desconto|poup|economiz")
    private val onlyPriceLine = Regex("^\\s*€?\\s*\\d{1,3}\\s\\d{2}\\s*€?\\s*$")

    fun parse(lines: List<OcrLine>): PriceParseResult {
        val found = lines.flatMap { line -> parseLine(line.text).map { it to line.height } }
        val maxHeight = found.maxOfOrNull { it.second } ?: 0

        val candidates = found
            .map { (match, height) -> match.toCandidate(height, maxHeight) }
            .groupBy { it.cents to it.kind }
            .map { (_, same) -> same.maxBy { it.confidence } }
            .sortedWith(compareByDescending<PriceCandidate> { it.kind.isSalePrice() }.thenByDescending { it.confidence })

        val suggested = candidates.firstOrNull { it.kind.isSalePrice() && it.confidence >= MIN_SUGGESTION_CONFIDENCE }
        return PriceParseResult(candidates, suggested, lines.joinToString("\n") { it.text })
    }

    private fun PriceKind.isSalePrice() = this == PriceKind.SALE || this == PriceKind.UNKNOWN

    private class RawMatch(
        val cents: Long,
        val kind: PriceKind,
        val euroAdjacent: Boolean,
        val promo: Boolean,
        val text: String,
    )

    private fun parseLine(text: String): List<RawMatch> {
        val line = text.replace('\u00A0', ' ')
        val lower = line.lowercase()
        val matches = numberPattern.findAll(line).toList()
        var previousEnd = 0
        return matches.mapNotNull { match ->
            val prefix = lower.substring(0, match.range.first)
            val suffix = lower.substring(match.range.last + 1)
            // Palavras-chave valem desde o número anterior; se for o único número, vale a linha toda.
            val segment = if (matches.size == 1) lower else lower.substring(previousEnd, match.range.first)
            previousEnd = match.range.last + 1

            val g = match.groupValues
            val euroInside = g[1].isNotEmpty()
            val spaced = g[5].isNotEmpty()
            val euros = g[1].ifEmpty { g[3] }.ifEmpty { g[5] }
            val cents = g[2].ifEmpty { g[4] }.ifEmpty { g[6] }
            if (suffix.trimStart().startsWith("%")) return@mapNotNull null

            val euroAdjacent = euroInside ||
                prefix.trimEnd().endsWith("€") ||
                suffix.trimStart().let { it.startsWith("€") || it.startsWith("eur") }
            if (spaced && !euroAdjacent && !onlyPriceLine.matches(lower)) return@mapNotNull null

            val value = euros.toLong() * 100 + cents.toLong()
            if (value <= 0) return@mapNotNull null

            val promo = promoWords.containsMatchIn(segment)
            val kind = when {
                prefix.endsWith("-") || discountWords.containsMatchIn(segment) -> PriceKind.DISCOUNT
                perKgAfter.containsMatchIn(suffix) || perKgBefore.containsMatchIn(prefix) -> PriceKind.PER_KG
                perLAfter.containsMatchIn(suffix) || perLBefore.containsMatchIn(prefix) -> PriceKind.PER_L
                oldWords.containsMatchIn(segment) -> PriceKind.OLD
                euroAdjacent || promo -> PriceKind.SALE
                else -> PriceKind.UNKNOWN
            }
            RawMatch(value, kind, euroAdjacent, promo, line.trim())
        }
    }

    private fun RawMatch.toCandidate(height: Int, maxHeight: Int): PriceCandidate {
        var confidence = if (kind == PriceKind.UNKNOWN) 0.3f else 0.5f
        if (euroAdjacent) confidence += 0.15f
        if (promo) confidence += 0.1f
        if (maxHeight > 0) confidence += 0.25f * height / maxHeight
        confidence = (confidence.coerceIn(0f, 1f) * 100).roundToInt() / 100f
        return PriceCandidate(cents, kind, confidence, text)
    }
}
