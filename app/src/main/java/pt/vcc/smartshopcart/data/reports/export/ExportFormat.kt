package pt.vcc.smartshopcart.data.reports.export

/** Formatos de exportação; [mvp] indica os que já têm exportador (os restantes ficam preparados). */
enum class ExportFormat(val extension: String, val mimeType: String, val mvp: Boolean) {
    CSV("csv", "text/csv", true),
    JSON("json", "application/json", true),
    PDF("pdf", "application/pdf", true),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false),
    HTML("html", "text/html", false),
    TXT("txt", "text/plain", false),
}

/** Uma linha por artigo ou uma linha por transação (compra/despesa). */
enum class ExportGranularity { ITEMS, TRANSACTIONS }

data class ExportOptions(val granularity: ExportGranularity = ExportGranularity.ITEMS)
