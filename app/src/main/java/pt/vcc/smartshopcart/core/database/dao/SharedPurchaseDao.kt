package pt.vcc.smartshopcart.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.core.database.entity.ShoppingItemAssignmentEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingParticipantEntity

@Dao
interface SharedPurchaseDao {
    @Query("SELECT * FROM shopping_participants WHERE sessionId = :sessionId ORDER BY sortOrder, name")
    fun observeParticipants(sessionId: String): Flow<List<ShoppingParticipantEntity>>

    @Query("""SELECT a.* FROM shopping_item_assignments a
        JOIN shopping_session_items i ON i.id = a.itemId
        WHERE i.sessionId = :sessionId""")
    fun observeAssignments(sessionId: String): Flow<List<ShoppingItemAssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addParticipant(person: ShoppingParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assign(assignment: ShoppingItemAssignmentEntity)

    @Query("DELETE FROM shopping_item_assignments WHERE itemId = :itemId")
    suspend fun unassign(itemId: String)

    @Query("DELETE FROM shopping_participants WHERE id = :participantId")
    suspend fun removeParticipant(participantId: String)

    @Query("SELECT * FROM shopping_participants WHERE id = :participantId LIMIT 1")
    suspend fun getParticipant(participantId: String): ShoppingParticipantEntity?

    @Query("SELECT sessionId FROM shopping_session_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemSessionId(itemId: String): String?
}
