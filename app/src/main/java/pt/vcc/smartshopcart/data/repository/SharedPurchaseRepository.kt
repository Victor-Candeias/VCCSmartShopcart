package pt.vcc.smartshopcart.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.entity.ShoppingItemAssignmentEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingParticipantEntity
import pt.vcc.smartshopcart.feature.sharedshopping.PurchaseParticipant
import java.util.UUID
import javax.inject.Inject

data class SharedPurchaseData(
    val participants: List<PurchaseParticipant>,
    val assignments: Map<String, String>,
)

class SharedPurchaseRepository @Inject constructor(private val database: ShoppingDatabase) {
    private val dao get() = database.sharedPurchaseDao()

    fun observe(sessionId: String): Flow<SharedPurchaseData> =
        combine(dao.observeParticipants(sessionId), dao.observeAssignments(sessionId)) { people, assignments ->
            SharedPurchaseData(
                people.map { PurchaseParticipant(it.id, it.name, it.sortOrder) },
                assignments.associate { it.itemId to it.participantId },
            )
        }

    suspend fun addParticipant(sessionId: String, name: String) {
        require(name.isNotBlank())
        database.withTransaction {
            requireNotNull(database.shoppingSessionDao().getSession(sessionId))
            dao.addParticipant(
                ShoppingParticipantEntity(UUID.randomUUID().toString(), sessionId, name.trim(), 0),
            )
        }
    }

    suspend fun assign(sessionId: String, itemId: String, participantId: String?) {
        database.withTransaction {
            require(dao.getItemSessionId(itemId) == sessionId) { "Produto fora da compra" }
            if (participantId == null) {
                dao.unassign(itemId)
            } else {
                require(dao.getParticipant(participantId)?.sessionId == sessionId) {
                    "Cliente fora da compra"
                }
                dao.assign(ShoppingItemAssignmentEntity(itemId, participantId))
            }
        }
    }

    suspend fun removeParticipant(sessionId: String, participantId: String) {
        database.withTransaction {
            require(dao.getParticipant(participantId)?.sessionId == sessionId)
            dao.removeParticipant(participantId)
        }
    }
}
