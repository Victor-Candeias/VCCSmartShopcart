package pt.vcc.smartshopcart.data.reports

import pt.vcc.smartshopcart.core.database.dao.ReportBudgetDb
import pt.vcc.smartshopcart.core.database.dao.ReportRowDb
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.reports.ReportBudgetRow
import pt.vcc.smartshopcart.domain.reports.ReportLabels
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportRow
import java.math.BigDecimal
import java.time.Instant

/**
 * Estabelecimentos (lojas das compras e estabelecimentos das despesas) agrupam-se pelo nome
 * normalizado, para "Continente" contar como um só local em todos os modos.
 */
internal fun establishmentKey(name: String?): String? =
    name?.trim()?.takeIf { it.isNotEmpty() }?.let { "place:${it.lowercase()}" }

internal fun ReportRowDb.toDomain(): ReportRow = ReportRow(
    transactionId = transactionId,
    itemId = itemId,
    occurredAt = Instant.ofEpochMilli(occurredAtMillis),
    mode = ReportMode.valueOf(mode),
    establishmentId = establishmentKey(establishmentName),
    establishmentName = establishmentName?.trim()?.takeIf { it.isNotEmpty() },
    categoryId = categoryId?.takeIf { categoryName != null },
    categoryName = categoryName?.trim()?.takeIf { it.isNotEmpty() },
    productId = productId,
    productName = productName?.trim()?.takeIf { it.isNotEmpty() } ?: ReportLabels.UNNAMED_PRODUCT,
    quantity = quantityText.toBigDecimalOrNull() ?: BigDecimal.ONE,
    unit = runCatching { MeasureUnit.valueOf(unit).symbol }.getOrDefault(unit),
    unitPriceCents = unitPriceCents,
    discountCents = discountCents,
    totalCents = totalCents,
)

internal fun ReportBudgetDb.toDomain(): ReportBudgetRow = ReportBudgetRow(
    transactionId = transactionId,
    mode = ReportMode.valueOf(mode),
    occurredAt = Instant.ofEpochMilli(occurredAtMillis),
    label = listOfNotNull(listName, establishmentName).joinToString(" · ").ifBlank { ReportLabels.mode(ReportMode.valueOf(mode)) },
    establishmentId = establishmentKey(establishmentName),
    budgetCents = budgetCents,
    spentCents = spentCents,
)
