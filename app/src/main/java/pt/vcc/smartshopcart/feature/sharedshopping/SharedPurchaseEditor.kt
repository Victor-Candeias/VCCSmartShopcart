package pt.vcc.smartshopcart.feature.sharedshopping

/**
 * Operacoes imutaveis para a futura integracao com a sessao Room existente.
 * A persistencia e a UI devem invocar estas regras, nao duplicar calculos.
 */
object SharedPurchaseEditor {
    fun assign(
        lines: List<PurchaseLine>,
        lineId: String,
        participantId: String?,
        participants: List<PurchaseParticipant>,
    ): List<PurchaseLine> {
        require(participantId == null || participants.any { it.id == participantId }) {
            "Participante desconhecido"
        }
        require(lines.any { it.id == lineId }) { "Produto inexistente" }
        return lines.map { if (it.id == lineId) it.copy(participantId = participantId) else it }
    }

    fun removeParticipant(
        participants: List<PurchaseParticipant>,
        lines: List<PurchaseLine>,
        participantId: String,
    ): Pair<List<PurchaseParticipant>, List<PurchaseLine>> {
        require(participants.any { it.id == participantId })
        return participants.filterNot { it.id == participantId } to
            lines.map { if (it.participantId == participantId) it.copy(participantId = null) else it }
    }
}
