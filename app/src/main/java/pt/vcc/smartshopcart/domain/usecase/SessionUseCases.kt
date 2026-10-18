package pt.vcc.smartshopcart.domain.usecase

import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.money.LineTotalCalculator
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PlannedItemStatus
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingSession
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.repository.StoreRepository
import java.math.BigDecimal
import javax.inject.Inject

class SessionNotActiveException : IllegalStateException("A sessão já não está ativa")

sealed interface StartSessionResult {
    data class Started(val session: ShoppingSession) : StartSessionResult

    /** D15: só pode existir uma sessão ativa. */
    data class ActiveSessionExists(val session: ShoppingSession) : StartSessionResult
}

class StartShoppingSessionUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
    private val stores: StoreRepository,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        mode: SessionMode,
        shoppingListId: String? = null,
        storeName: String? = null,
        budgetCents: Long? = null,
    ): StartSessionResult {
        sessions.getActiveSession()?.let { return StartSessionResult.ActiveSessionExists(it) }
        val store = storeName?.takeIf { it.isNotBlank() }?.let { stores.getOrCreate(it) }
        val session = ShoppingSession(
            id = ids.newId(),
            shoppingListId = shoppingListId,
            mode = mode,
            status = SessionStatus.ACTIVE,
            storeId = store?.id,
            budgetCents = budgetCents?.takeIf { it > 0 },
            startedAt = time.now(),
            completedAt = null,
            finalTotalCents = null,
        )
        sessions.createSession(session)
        return StartSessionResult.Started(session)
    }
}

data class NewSessionItem(
    val unitPriceCents: Long,
    val quantity: BigDecimal = BigDecimal.ONE,
    val unit: MeasureUnit = MeasureUnit.UN,
    val discountCents: Long = 0,
    val name: String? = null,
    val barcode: String? = null,
    val productId: String? = null,
    val shoppingListItemId: String? = null,
    val priceSource: PriceSource = PriceSource.MANUAL,
)

class AddSessionItemUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(sessionId: String, item: NewSessionItem): ShoppingSessionItem {
        val session = sessions.getSession(sessionId)
        if (session?.status != SessionStatus.ACTIVE) throw SessionNotActiveException()
        val created = ShoppingSessionItem(
            id = ids.newId(),
            sessionId = sessionId,
            shoppingListItemId = item.shoppingListItemId,
            productId = item.productId,
            name = item.name?.trim()?.takeIf { it.isNotEmpty() },
            barcode = item.barcode,
            quantity = item.quantity,
            unit = item.unit,
            unitPriceCents = item.unitPriceCents,
            discountCents = item.discountCents,
            totalCents = LineTotalCalculator.lineTotalCents(item.unitPriceCents, item.quantity, item.unit, item.discountCents),
            priceSource = item.priceSource,
            addedAt = time.now(),
        )
        sessions.addItem(created)
        item.shoppingListItemId?.let { sessions.setPlannedItemStatus(sessionId, it, PlannedItemStatus.ADDED) }
        return created
    }
}

class UpdateSessionItemUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
) {
    /** Recalcula o total da linha a partir do preço, quantidade, unidade e desconto. */
    suspend operator fun invoke(item: ShoppingSessionItem): ShoppingSessionItem {
        if (sessions.getSession(item.sessionId)?.status != SessionStatus.ACTIVE) throw SessionNotActiveException()
        val updated = item.copy(
            name = item.name?.trim()?.takeIf { it.isNotEmpty() },
            totalCents = LineTotalCalculator.lineTotalCents(item.unitPriceCents, item.quantity, item.unit, item.discountCents),
        )
        sessions.updateItem(updated)
        return updated
    }
}

class RemoveSessionItemUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
) {
    /** Devolve o item removido para permitir desfazer. */
    suspend operator fun invoke(itemId: String): ShoppingSessionItem? {
        val item = sessions.getItem(itemId) ?: return null
        if (sessions.getSession(item.sessionId)?.status != SessionStatus.ACTIVE) throw SessionNotActiveException()
        sessions.removeItem(itemId)
        return item
    }
}

class RestoreSessionItemUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
) {
    suspend operator fun invoke(item: ShoppingSessionItem) {
        if (sessions.getSession(item.sessionId)?.status != SessionStatus.ACTIVE) throw SessionNotActiveException()
        if (sessions.getItem(item.id) == null) sessions.addItem(item)
    }
}

class CompleteShoppingSessionUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(sessionId: String): ShoppingSession =
        try {
            sessions.completeSession(sessionId, time.now(), ids::newId)
        } catch (e: IllegalStateException) {
            throw SessionNotActiveException()
        }
}

class CancelShoppingSessionUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(sessionId: String) {
        val session = sessions.getSession(sessionId) ?: return
        if (session.status != SessionStatus.ACTIVE) throw SessionNotActiveException()
        sessions.updateSession(session.copy(status = SessionStatus.CANCELLED, completedAt = time.now()))
    }
}
