package pt.vcc.smartshopcart.domain.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.domain.model.PlannedItemStatus
import pt.vcc.smartshopcart.domain.model.ShoppingSession
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.model.ShoppingSessionWithItems

data class SessionSummary(
    val session: ShoppingSession,
    val storeName: String?,
    val listName: String?,
    val itemCount: Int,
)

interface ShoppingSessionRepository {
    fun observeActiveSession(): Flow<ShoppingSession?>
    fun observeSession(id: String): Flow<ShoppingSessionWithItems?>
    fun observeCompletedSessions(): Flow<List<SessionSummary>>
    fun observeSkippedListItems(sessionId: String): Flow<Set<String>>

    suspend fun getActiveSession(): ShoppingSession?
    suspend fun getSession(id: String): ShoppingSession?
    suspend fun getItem(id: String): ShoppingSessionItem?

    suspend fun createSession(session: ShoppingSession)
    suspend fun updateSession(session: ShoppingSession)
    suspend fun addItem(item: ShoppingSessionItem)
    suspend fun updateItem(item: ShoppingSessionItem)
    suspend fun removeItem(id: String)
    suspend fun setPlannedItemStatus(sessionId: String, listItemId: String, status: PlannedItemStatus)

    /** Conclui numa transação: total consolidado e histórico de preços dos itens identificáveis. */
    suspend fun completeSession(id: String, completedAt: Long, newHistoryId: () -> String): ShoppingSession

    suspend fun deleteSession(id: String)
}
