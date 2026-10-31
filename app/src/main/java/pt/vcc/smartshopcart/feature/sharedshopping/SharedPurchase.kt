package pt.vcc.smartshopcart.feature.sharedshopping

import java.util.UUID

/**
 * Participantes locais de uma compra. Nao representam contas autenticadas.
 * Os valores monetarios sao sempre inteiros em centimos.
 */
data class PurchaseParticipant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val order: Int = 0,
)

data class PurchaseLine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int = 1,
    val unitPriceCents: Long,
    val discountCents: Long = 0,
    val participantId: String? = null,
) {
    init {
        require(quantity > 0) { "A quantidade deve ser positiva" }
        require(unitPriceCents >= 0) { "O preco nao pode ser negativo" }
        require(discountCents >= 0) { "O desconto nao pode ser negativo" }
        require(discountCents <= Math.multiplyExact(unitPriceCents, quantity.toLong())) {
            "O desconto nao pode exceder o subtotal"
        }
    }

    val totalCents: Long
        get() = Math.subtractExact(
            Math.multiplyExact(unitPriceCents, quantity.toLong()),
            discountCents,
        )
}

data class SharedPurchaseSummary(
    val grandTotalCents: Long,
    val totalsByParticipant: Map<String, Long>,
    val unassignedCents: Long,
    val canFinish: Boolean,
)

object SharedPurchaseCalculator {
    fun calculate(
        participants: List<PurchaseParticipant>,
        lines: List<PurchaseLine>,
    ): SharedPurchaseSummary {
        require(participants.map { it.id }.distinct().size == participants.size) {
            "IDs de participantes duplicados"
        }
        val ids = participants.map { it.id }.toSet()
        require(lines.all { it.participantId == null || it.participantId in ids }) {
            "Produto atribuido a participante inexistente"
        }

        val totals = participants.associate { it.id to 0L }.toMutableMap()
        var grandTotal = 0L
        var unassigned = 0L
        for (line in lines) {
            val amount = line.totalCents
            grandTotal = Math.addExact(grandTotal, amount)
            if (line.participantId == null) {
                unassigned = Math.addExact(unassigned, amount)
            } else {
                totals[line.participantId] = Math.addExact(totals.getValue(line.participantId), amount)
            }
        }
        check(Math.addExact(totals.values.fold(0L, Math::addExact), unassigned) == grandTotal)
        return SharedPurchaseSummary(
            grandTotalCents = grandTotal,
            totalsByParticipant = totals.toMap(),
            unassignedCents = unassigned,
            canFinish = lines.isNotEmpty() && lines.all { it.participantId != null },
        )
    }
}
