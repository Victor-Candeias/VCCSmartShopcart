package pt.vcc.smartshopcart.domain.usecase

import pt.vcc.smartshopcart.domain.model.PlannedItemStatus
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingListItem
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import javax.inject.Inject

data class PlannedEntry(
    val listItem: ShoppingListItem,
    val status: PlannedItemStatus,
    /** Itens de sessão ligados a este item planeado (normalmente um). */
    val sessionItems: List<ShoppingSessionItem>,
)

data class PlannedProgress(
    val entries: List<PlannedEntry>,
    val extras: List<ShoppingSessionItem>,
) {
    val pending get() = entries.filter { it.status == PlannedItemStatus.PENDING }
    val added get() = entries.filter { it.status == PlannedItemStatus.ADDED }
    val skipped get() = entries.filter { it.status == PlannedItemStatus.SKIPPED }
    val totalCount get() = entries.size
    val resolvedCount get() = entries.count { it.status != PlannedItemStatus.PENDING }
    val fraction: Float get() = if (entries.isEmpty()) 1f else resolvedCount.toFloat() / entries.size
}

/**
 * Deriva o estado de cada item planeado (D12): `ADDED` se existir item de sessão ligado,
 * `SKIPPED` se persistido como ignorado, caso contrário `PENDING`. Itens de sessão sem
 * ligação a um item da lista são extras.
 */
class DerivePlannedProgressUseCase @Inject constructor() {
    operator fun invoke(
        listItems: List<ShoppingListItem>,
        sessionItems: List<ShoppingSessionItem>,
        skippedListItemIds: Set<String>,
    ): PlannedProgress {
        val byListItem = sessionItems.filter { it.shoppingListItemId != null }.groupBy { it.shoppingListItemId }
        val listIds = listItems.mapTo(HashSet()) { it.id }
        val entries = listItems.map { listItem ->
            val linked = byListItem[listItem.id].orEmpty()
            val status = when {
                linked.isNotEmpty() -> PlannedItemStatus.ADDED
                listItem.id in skippedListItemIds -> PlannedItemStatus.SKIPPED
                else -> PlannedItemStatus.PENDING
            }
            PlannedEntry(listItem, status, linked)
        }
        val extras = sessionItems.filter { it.shoppingListItemId == null || it.shoppingListItemId !in listIds }
        return PlannedProgress(entries, extras)
    }
}

class SetPlannedItemSkippedUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
) {
    suspend operator fun invoke(sessionId: String, listItemId: String, skipped: Boolean) {
        if (sessions.getSession(sessionId)?.status != SessionStatus.ACTIVE) throw SessionNotActiveException()
        sessions.setPlannedItemStatus(
            sessionId,
            listItemId,
            if (skipped) PlannedItemStatus.SKIPPED else PlannedItemStatus.PENDING,
        )
    }
}
