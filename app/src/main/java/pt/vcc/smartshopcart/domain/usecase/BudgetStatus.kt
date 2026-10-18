package pt.vcc.smartshopcart.domain.usecase

import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import javax.inject.Inject

enum class BudgetLevel { NONE, OK, WARNING, EXCEEDED }

data class BudgetStatus(
    val totalCents: Long,
    val budgetCents: Long?,
    val remainingCents: Long?,
    val level: BudgetLevel,
    /** Fração usada do orçamento (0..∞), para a barra de progresso. */
    val usedFraction: Float,
)

class CalculateSessionTotalUseCase @Inject constructor() {
    operator fun invoke(items: List<ShoppingSessionItem>): Long = LineTotalCalculator.sum(items.map { it.totalCents })
}

class CalculateRemainingBudgetUseCase @Inject constructor() {
    operator fun invoke(totalCents: Long, budgetCents: Long?, warningPercent: Int): BudgetStatus {
        if (budgetCents == null || budgetCents <= 0) {
            return BudgetStatus(totalCents, null, null, BudgetLevel.NONE, 0f)
        }
        val remaining = budgetCents - totalCents
        val level = when {
            remaining < 0 -> BudgetLevel.EXCEEDED
            totalCents * 100 >= budgetCents * warningPercent -> BudgetLevel.WARNING
            else -> BudgetLevel.OK
        }
        return BudgetStatus(totalCents, budgetCents, remaining, level, totalCents.toFloat() / budgetCents)
    }
}
