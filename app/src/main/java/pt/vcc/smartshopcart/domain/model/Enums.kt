package pt.vcc.smartshopcart.domain.model

import java.math.BigDecimal

enum class SessionMode { CALCULATOR, PLANNED }

enum class SessionStatus { ACTIVE, COMPLETED, CANCELLED }

/** `OPEN_PRICES`: preço de referência comunitário confirmado sem alteração pelo utilizador (OP14). */
enum class PriceSource { MANUAL, OCR, HISTORY, OPEN_PRICES }

enum class ProductSource { MANUAL, OPEN_FOOD_FACTS }

enum class CaptureStatus { PENDING, CONFIRMED, REJECTED }

enum class PlannedItemStatus { PENDING, ADDED, SKIPPED }

/**
 * Unidade da quantidade. O preço é sempre por [priceUnit] (D19): `G` e `ML` são convertidos
 * para `KG` e `L` antes de multiplicar.
 */
enum class MeasureUnit(val symbol: String) {
    UN("un"),
    KG("kg"),
    G("g"),
    L("L"),
    ML("ml");

    val priceUnit: MeasureUnit
        get() = when (this) {
            G -> KG
            ML -> L
            else -> this
        }

    fun toPriceUnitQuantity(quantity: BigDecimal): BigDecimal = when (this) {
        G, ML -> quantity.movePointLeft(3)
        else -> quantity
    }
}
