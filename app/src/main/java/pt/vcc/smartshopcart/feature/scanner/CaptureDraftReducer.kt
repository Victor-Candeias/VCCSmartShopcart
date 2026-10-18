package pt.vcc.smartshopcart.feature.scanner

import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.MoneyParser
import pt.vcc.smartshopcart.core.money.QuantityParser
import pt.vcc.smartshopcart.core.ocr.PriceParseResult
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.PriceCandidate
import pt.vcc.smartshopcart.domain.model.PriceKind
import pt.vcc.smartshopcart.domain.model.ReferencePrice
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.usecase.ProductLookupStatus
import pt.vcc.smartshopcart.domain.usecase.ResolveProductResult
import java.io.Serializable
import java.math.BigDecimal

/**
 * Rascunho de uma captura (`ProductCaptureDraft`, §5.4): junta o produto (código de barras) e o preço
 * (OCR ou manual) de uma ou várias capturas. Fica em `SavedStateHandle` (D25).
 */
data class ScanDraft(
    val barcode: String? = null,
    val status: ProductLookupStatus? = null,
    val productId: String? = null,
    val brand: String? = null,
    val quantityLabel: String? = null,
    val name: String = "",
    val price: String = "",
    val quantity: String = "1",
    val unit: MeasureUnit = MeasureUnit.UN,
    /** Último preço no mesmo supermercado (D17). */
    val suggestedPriceCents: Long? = null,
    val suggestedStoreName: String? = null,
    /** Preço Open Prices escolhido com "Usar como referência" (OP14); só conta se o utilizador o confirmar. */
    val referencePriceCents: Long? = null,
    val priceCandidates: List<PriceCandidate> = emptyList(),
    val selectedCandidate: Int? = null,
    val ocrRawText: String? = null,
    /** Nomes lidos na fotografia do nome, por ordem de sugestão. */
    val nameCandidates: List<String> = emptyList(),
) : Serializable {
    val priceCents: Long? get() = MoneyParser.parseToCents(price)?.takeIf { it > 0 }
    val quantityValue: BigDecimal? get() = QuantityParser.parse(quantity)
    val ocrPriceCents: Long? get() = selectedCandidate?.let { priceCandidates.getOrNull(it)?.cents }

    /** O preço no campo é o que o OCR leu (ainda por confirmar pelo utilizador). */
    val priceFromOcr: Boolean get() = ocrPriceCents != null && ocrPriceCents == priceCents

    /** O preço no campo é o de referência Open Prices escolhido (ainda por confirmar pelo utilizador). */
    val priceFromReference: Boolean get() = referencePriceCents != null && referencePriceCents == priceCents

    val totalCents: Long?
        get() {
            val p = priceCents ?: return null
            val q = quantityValue ?: return null
            return LineTotalCalculator.lineTotalCents(p, q, unit)
        }
}

/** Regras para juntar capturas sucessivas, em qualquer ordem, num único rascunho (§5.1 B). */
object CaptureDraftReducer {

    fun start(listItem: ShoppingListItem?) = ScanDraft(
        name = listItem?.name.orEmpty(),
        price = listItem?.estimatedPriceCents?.let(MoneyFormatter::formatPlain).orEmpty(),
        quantity = QuantityParser.format(listItem?.plannedQuantity ?: BigDecimal.ONE),
        unit = listItem?.unit ?: MeasureUnit.UN,
    )

    /**
     * Junta o produto identificado. O mesmo código lido de novo não altera o rascunho; um código diferente
     * começa um rascunho novo (é outro produto).
     */
    fun withProduct(draft: ScanDraft?, result: ResolveProductResult.Resolved, listItem: ShoppingListItem?): ScanDraft {
        if (draft?.barcode == result.barcode) return draft
        val base = if (draft == null || draft.barcode != null) start(listItem) else draft
        val ocrPrice = base.ocrPriceCents != null
        val canAdoptUnit = listItem == null && !ocrPrice
        val suggestion = result.suggestedPrice?.takeIf { canAdoptUnit || it.unit.priceUnit == base.unit.priceUnit }
        val unit = if (canAdoptUnit && suggestion != null) suggestion.unit else base.unit
        val product = result.product
        return base.copy(
            barcode = result.barcode,
            status = result.status,
            productId = product?.id,
            brand = product?.brand,
            quantityLabel = product?.quantityLabel,
            name = product?.name ?: base.name,
            unit = unit,
            price = if (!ocrPrice && suggestion != null) MoneyFormatter.formatPlain(suggestion.priceCents) else base.price,
            suggestedPriceCents = suggestion?.priceCents,
            suggestedStoreName = suggestion?.storeName,
        )
    }

    /** Junta os candidatos de preço de uma fotografia; devolve `null` se não houver nenhum. */
    fun withPrices(draft: ScanDraft?, parse: PriceParseResult, listItem: ShoppingListItem?): ScanDraft? {
        if (parse.candidates.isEmpty()) return null
        val updated = (draft ?: start(listItem)).copy(
            priceCandidates = parse.candidates,
            selectedCandidate = null,
            ocrRawText = parse.rawText,
        )
        val suggested = parse.suggested ?: return updated
        return selectCandidate(updated, parse.candidates.indexOf(suggested))
    }

    /** Usa um candidato como preço; preços por kg/L passam a unidade para kg/L. */
    fun selectCandidate(draft: ScanDraft, index: Int): ScanDraft {
        val candidate = draft.priceCandidates.getOrNull(index) ?: return draft
        val unit = when (candidate.kind) {
            PriceKind.PER_KG -> if (draft.unit.priceUnit == MeasureUnit.KG) draft.unit else MeasureUnit.KG
            PriceKind.PER_L -> if (draft.unit.priceUnit == MeasureUnit.L) draft.unit else MeasureUnit.L
            else -> draft.unit
        }
        return draft.copy(selectedCandidate = index, price = MoneyFormatter.formatPlain(candidate.cents), unit = unit)
    }

    /** Nomes lidos numa fotografia: o primeiro preenche o nome; devolve `null` se não houver nenhum. */
    fun withNames(draft: ScanDraft?, names: List<String>, listItem: ShoppingListItem?): ScanDraft? {
        val first = names.firstOrNull() ?: return null
        return (draft ?: start(listItem)).copy(name = first, nameCandidates = names)
    }

    /** Preço escrito à mão no lugar da fotografia; os candidatos do OCR ficam disponíveis mas não selecionados. */
    fun withManualPrice(draft: ScanDraft?, cents: Long, listItem: ShoppingListItem?): ScanDraft =
        (draft ?: start(listItem)).copy(price = MoneyFormatter.formatPlain(cents), selectedCandidate = null)

    /**
     * "Usar como referência" (OP14): só preenche o preço, que o utilizador tem de confirmar com "Adicionar".
     * Preços por kg passam a unidade para kg e preços por embalagem para unidades (OP7).
     */
    fun withReferencePrice(draft: ScanDraft, price: ReferencePrice): ScanDraft {
        val unit = when (price.basis) {
            PriceBasis.KILOGRAM -> if (draft.unit.priceUnit == MeasureUnit.KG) draft.unit else MeasureUnit.KG
            PriceBasis.UNIT -> if (draft.unit.priceUnit == MeasureUnit.UN) draft.unit else MeasureUnit.UN
        }
        return draft.copy(
            price = MoneyFormatter.formatPlain(price.priceCents),
            unit = unit,
            selectedCandidate = null,
            referencePriceCents = price.priceCents,
        )
    }
}
