package pt.vcc.smartshopcart.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_participants",
    foreignKeys = [ForeignKey(
        entity = ShoppingSessionEntity::class,
        parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class ShoppingParticipantEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "shopping_item_assignments",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingSessionItemEntity::class,
            parentColumns = ["id"], childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShoppingParticipantEntity::class,
            parentColumns = ["id"], childColumns = ["participantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("participantId")],
)
data class ShoppingItemAssignmentEntity(
    @PrimaryKey val itemId: String,
    val participantId: String,
)
