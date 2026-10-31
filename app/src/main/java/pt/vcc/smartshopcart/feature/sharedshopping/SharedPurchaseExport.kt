package pt.vcc.smartshopcart.feature.sharedshopping

import java.util.Locale

/** Exportacao de detalhe por participante, independente do formato de relatorio geral. */
object SharedPurchaseExport {
    private fun csv(value: String): String {
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun csv(
        participants: List<PurchaseParticipant>,
        lines: List<PurchaseLine>,
    ): String {
        val names = participants.associate { it.id to it.name }
        val header = "Cliente;Produto;Quantidade;Total (EUR)"
        return (listOf(header) + lines.map { line ->
            listOf(
                csv(names[line.participantId] ?: "Por atribuir"),
                csv(line.name),
                line.quantity.toString(),
                String.format(Locale.ROOT, "%.2f", line.totalCents / 100.0).replace('.', ','),
            ).joinToString(";")
        }).joinToString("\r\n")
    }

    fun summaryText(
        participants: List<PurchaseParticipant>,
        lines: List<PurchaseLine>,
    ): String {
        val summary = SharedPurchaseCalculator.calculate(participants, lines)
        return buildString {
            appendLine("Compra partilhada")
            participants.forEach { person ->
                appendLine("${person.name}: ${summary.totalsByParticipant.getValue(person.id)} centimos")
            }
            if (summary.unassignedCents != 0L) appendLine("Por atribuir: ${summary.unassignedCents} centimos")
            appendLine("Total: ${summary.grandTotalCents} centimos")
        }
    }
}
