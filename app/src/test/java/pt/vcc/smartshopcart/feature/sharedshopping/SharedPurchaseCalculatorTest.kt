package pt.vcc.smartshopcart.feature.sharedshopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPurchaseCalculatorTest {
    private val alice = PurchaseParticipant(id = "a", name = "Alice")
    private val bob = PurchaseParticipant(id = "b", name = "Bob")

    @Test fun totalsByParticipantEqualGrandTotal() {
        val lines = listOf(
            PurchaseLine(name = "Leite", quantity = 2, unitPriceCents = 125, participantId = "a"),
            PurchaseLine(name = "Pao", unitPriceCents = 180, participantId = "b"),
        )
        val result = SharedPurchaseCalculator.calculate(listOf(alice, bob), lines)
        assertEquals(430L, result.grandTotalCents)
        assertEquals(250L, result.totalsByParticipant["a"])
        assertEquals(180L, result.totalsByParticipant["b"])
        assertEquals(0L, result.unassignedCents)
        assertTrue(result.canFinish)
    }

    @Test fun unassignedProductPreventsFinishing() {
        val result = SharedPurchaseCalculator.calculate(
            listOf(alice), listOf(PurchaseLine(name = "Cafe", unitPriceCents = 85)),
        )
        assertEquals(85L, result.unassignedCents)
        assertFalse(result.canFinish)
    }

    @Test fun removalReturnsLinesToUnassigned() {
        val lines = listOf(PurchaseLine(name = "Cafe", unitPriceCents = 85, participantId = "a"))
        val (people, updated) = SharedPurchaseEditor.removeParticipant(listOf(alice, bob), lines, "a")
        assertEquals(1, people.size)
        assertEquals(null, updated.single().participantId)
    }

    @Test fun discountsAreAppliedBeforeAllocation() {
        val result = SharedPurchaseCalculator.calculate(
            listOf(alice),
            listOf(PurchaseLine(name = "Bolachas", quantity = 2, unitPriceCents = 200, discountCents = 50, participantId = "a")),
        )
        assertEquals(350L, result.grandTotalCents)
    }
}
