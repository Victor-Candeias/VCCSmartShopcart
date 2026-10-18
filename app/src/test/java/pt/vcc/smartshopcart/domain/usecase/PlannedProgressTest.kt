package pt.vcc.smartshopcart.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PlannedItemStatus
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import java.math.BigDecimal

class PlannedProgressTest {
    private val derive = DerivePlannedProgressUseCase()

    private fun listItem(id: String) = ShoppingListItem(id, "l", null, id, BigDecimal.ONE, MeasureUnit.UN, null, 0)

    private fun sessionItem(id: String, listItemId: String?) = ShoppingSessionItem(
        id, "s", listItemId, null, id, null, BigDecimal.ONE, MeasureUnit.UN, 100, 0, 100, PriceSource.MANUAL, 0,
    )

    @Test
    fun `deriva estados e extras`() {
        val progress = derive(
            listItems = listOf(listItem("a"), listItem("b"), listItem("c")),
            sessionItems = listOf(sessionItem("x", "a"), sessionItem("y", null), sessionItem("z", "removida")),
            skippedListItemIds = setOf("b"),
        )
        assertEquals(
            listOf(PlannedItemStatus.ADDED, PlannedItemStatus.SKIPPED, PlannedItemStatus.PENDING),
            progress.entries.map { it.status },
        )
        assertEquals(listOf("y", "z"), progress.extras.map { it.id })
        assertEquals(2, progress.resolvedCount)
        assertEquals(2f / 3f, progress.fraction, 0.0001f)
    }

    @Test
    fun `adicionado prevalece sobre ignorado e lista vazia esta completa`() {
        val progress = derive(listOf(listItem("a")), listOf(sessionItem("x", "a")), setOf("a"))
        assertEquals(PlannedItemStatus.ADDED, progress.entries.single().status)
        assertEquals(1f, derive(emptyList(), emptyList(), emptySet()).fraction)
    }
}
