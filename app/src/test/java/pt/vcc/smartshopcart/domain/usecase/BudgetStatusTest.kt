package pt.vcc.smartshopcart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetStatusTest {
    private val calculate = CalculateRemainingBudgetUseCase()

    @Test
    fun `sem orcamento`() {
        val status = calculate(totalCents = 500, budgetCents = null, warningPercent = 90)
        assertEquals(BudgetLevel.NONE, status.level)
        assertNull(status.remainingCents)
    }

    @Test
    fun `dentro do orcamento`() {
        val status = calculate(totalCents = 8999, budgetCents = 10000, warningPercent = 90)
        assertEquals(BudgetLevel.OK, status.level)
        assertEquals(1001L, status.remainingCents)
    }

    @Test
    fun `aviso a partir do limiar`() {
        assertEquals(BudgetLevel.WARNING, calculate(9000, 10000, 90).level)
        assertEquals(BudgetLevel.WARNING, calculate(10000, 10000, 90).level)
    }

    @Test
    fun `orcamento ultrapassado`() {
        val status = calculate(10001, 10000, 90)
        assertEquals(BudgetLevel.EXCEEDED, status.level)
        assertEquals(-1L, status.remainingCents)
    }
}
