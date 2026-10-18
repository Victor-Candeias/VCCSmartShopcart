package pt.vcc.smartshopcart.data.reports.export.pdf

import kotlin.math.max

enum class PdfTextStyle(val size: Float, val bold: Boolean) {
    TITLE(18f, true),
    HEADING(12f, true),
    BODY(9f, false),
    BODY_BOLD(9f, true),
    SMALL(7.5f, false),
    ;

    val lineHeight: Float get() = size * 1.35f
}

/** Mede a largura de texto em pontos; no Android é `Paint.measureText`, nos testes uma função fixa. */
fun interface PdfTextMeasurer {
    fun width(text: String, style: PdfTextStyle): Float
}

enum class PdfAlign { START, END }

object PdfColors {
    const val INK = 0xFF1F2933.toInt()
    const val MUTED = 0xFF616E7C.toInt()
    const val RULE = 0xFFCBD2D9.toInt()
    const val HEADER_FILL = 0xFFE4E7EB.toInt()
    const val ACCENT = 0xFF2E7D32.toInt()
    const val WARNING = 0xFFB54708.toInt()
}

/** Operação de desenho já posicionada; o renderizador só a traduz para `Canvas`. */
sealed interface PdfOp {
    data class Text(val x: Float, val baseline: Float, val text: String, val style: PdfTextStyle, val color: Int = PdfColors.INK) : PdfOp
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float = 0.5f, val color: Int = PdfColors.RULE) : PdfOp
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int) : PdfOp
    data class Polyline(val points: List<Pair<Float, Float>>, val width: Float = 1.5f, val color: Int = PdfColors.ACCENT) : PdfOp
}

data class PdfPage(val number: Int, val ops: List<PdfOp>)

data class PdfColumn(val title: String, val weight: Float, val align: PdfAlign = PdfAlign.START)

data class PdfTable(val columns: List<PdfColumn>, val rows: List<List<String>>, val emptyMessage: String)

/** Valor de um gráfico: [value] define a escala, [valueLabel] é o texto mostrado. */
data class PdfBar(val label: String, val value: Long, val valueLabel: String)

data class PdfKpi(val label: String, val value: String)

/** Conteúdo independente do Android, construído a partir do mesmo `ReportData` que o dashboard. */
data class PdfReportContent(
    val title: String,
    val runningHeader: String,
    val footerNote: String,
    val subtitleLines: List<String>,
    val kpis: List<PdfKpi>,
    val notes: List<String>,
    val warnings: List<String>,
    val categoriesTitle: String,
    val categories: List<PdfBar>,
    val timelineTitle: String,
    val timeline: List<PdfBar>,
    val tableTitle: String,
    val table: PdfTable,
    val emptyChartMessage: String,
)

data class PdfPageSpec(
    val width: Float = 595f,
    val height: Float = 842f,
    val margin: Float = 40f,
    val headerHeight: Float = 22f,
    val footerHeight: Float = 22f,
) {
    val contentTop: Float get() = margin + headerHeight
    val contentBottom: Float get() = height - margin - footerHeight
    val contentWidth: Float get() = width - 2 * margin

    companion object {
        val A4 = PdfPageSpec()
    }
}

/**
 * Paginação de relatórios A4: margens, cabeçalho e rodapé «Página X de N» em todas as páginas,
 * quebras de linha medidas, quebras de página, cabeçalho de tabela repetido e gráficos vetoriais.
 * Puro Kotlin para poder ser testado sem Android.
 */
class PdfLayoutEngine(
    private val measurer: PdfTextMeasurer,
    private val spec: PdfPageSpec = PdfPageSpec.A4,
) {
    private val pages = mutableListOf<MutableList<PdfOp>>()
    private var ops = mutableListOf<PdfOp>()
    private var y = 0f
    private val left get() = spec.margin
    private val right get() = spec.width - spec.margin

    fun layout(content: PdfReportContent): List<PdfPage> {
        pages.clear()
        newPage()
        title(content)
        kpis(content.kpis)
        paragraphs(content.notes, PdfColors.MUTED)
        paragraphs(content.warnings, PdfColors.WARNING)
        bars(content.categoriesTitle, content.categories, content.emptyChartMessage)
        timeline(content.timelineTitle, content.timeline, content.emptyChartMessage)
        table(content.tableTitle, content.table)
        val total = pages.size
        return pages.mapIndexed { index, pageOps ->
            PdfPage(index + 1, decorations(content, index + 1, total) + pageOps)
        }
    }

    /** Quebra [text] em linhas que cabem em [maxWidth]; palavras demasiado longas são partidas. */
    fun wrap(text: String, style: PdfTextStyle, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var current = ""
            paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (fits(candidate, style, maxWidth)) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) lines += current
                    var rest = word
                    while (!fits(rest, style, maxWidth) && rest.length > 1) {
                        val cut = longestPrefix(rest, style, maxWidth)
                        lines += rest.substring(0, cut)
                        rest = rest.substring(cut)
                    }
                    current = rest
                }
            }
            lines += current
        }
        return lines
    }

    /** Corta [text] com «…» para caber em [maxWidth]. */
    fun ellipsize(text: String, style: PdfTextStyle, maxWidth: Float): String {
        if (fits(text, style, maxWidth)) return text
        var end = text.length
        while (end > 0 && !fits(text.substring(0, end).trimEnd() + ELLIPSIS, style, maxWidth)) end--
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }

    private fun fits(text: String, style: PdfTextStyle, maxWidth: Float) = measurer.width(text, style) <= maxWidth

    private fun longestPrefix(word: String, style: PdfTextStyle, maxWidth: Float): Int {
        var end = 1
        while (end < word.length && fits(word.substring(0, end + 1), style, maxWidth)) end++
        return end
    }

    private fun newPage() {
        ops = mutableListOf()
        pages += ops
        y = spec.contentTop
    }

    private fun ensureSpace(height: Float) {
        if (y + height > spec.contentBottom && y > spec.contentTop) newPage()
    }

    private fun text(x: Float, line: String, style: PdfTextStyle, color: Int = PdfColors.INK) {
        ops += PdfOp.Text(x, y + style.size, line, style, color)
    }

    private fun textEnd(xRight: Float, line: String, style: PdfTextStyle, color: Int = PdfColors.INK) {
        ops += PdfOp.Text(xRight - measurer.width(line, style), y + style.size, line, style, color)
    }

    private fun lines(lines: List<String>, style: PdfTextStyle, color: Int = PdfColors.INK) {
        lines.forEach { line ->
            ensureSpace(style.lineHeight)
            text(left, line, style, color)
            y += style.lineHeight
        }
    }

    private fun decorations(content: PdfReportContent, number: Int, total: Int): List<PdfOp> {
        val small = PdfTextStyle.SMALL
        val headerRule = spec.margin + spec.headerHeight - 8f
        val footerTop = spec.height - spec.margin - small.lineHeight
        val pageLabel = "Página $number de $total"
        val pageWidth = measurer.width(pageLabel, small)
        val note = ellipsize(content.footerNote, small, spec.contentWidth - pageWidth - 12f)
        return listOf(
            PdfOp.Text(left, spec.margin + small.size, ellipsize(content.runningHeader, small, spec.contentWidth), small, PdfColors.MUTED),
            PdfOp.Line(left, headerRule, right, headerRule),
            PdfOp.Line(left, footerTop - 4f, right, footerTop - 4f),
            PdfOp.Text(left, footerTop + small.size, note, small, PdfColors.MUTED),
            PdfOp.Text(right - pageWidth, footerTop + small.size, pageLabel, small, PdfColors.MUTED),
        )
    }

    private fun title(content: PdfReportContent) {
        lines(wrap(content.title, PdfTextStyle.TITLE, spec.contentWidth), PdfTextStyle.TITLE)
        y += 2f
        content.subtitleLines.forEach { lines(wrap(it, PdfTextStyle.BODY, spec.contentWidth), PdfTextStyle.BODY, PdfColors.MUTED) }
        y += SECTION_GAP
    }

    private fun heading(title: String, keepWith: Float) {
        ensureSpace(PdfTextStyle.HEADING.lineHeight + 4f + keepWith)
        text(left, ellipsize(title, PdfTextStyle.HEADING, spec.contentWidth), PdfTextStyle.HEADING)
        y += PdfTextStyle.HEADING.lineHeight + 4f
    }

    private fun kpis(kpis: List<PdfKpi>) {
        if (kpis.isEmpty()) return
        val columns = 3
        val cellWidth = spec.contentWidth / columns
        val maxWidth = cellWidth - 8f
        val rowHeight = PdfTextStyle.SMALL.lineHeight + PdfTextStyle.HEADING.lineHeight + 8f
        kpis.chunked(columns).forEach { row ->
            ensureSpace(rowHeight)
            row.forEachIndexed { index, kpi ->
                val x = left + index * cellWidth
                ops += PdfOp.Text(x, y + PdfTextStyle.SMALL.size, ellipsize(kpi.label, PdfTextStyle.SMALL, maxWidth), PdfTextStyle.SMALL, PdfColors.MUTED)
                ops += PdfOp.Text(
                    x,
                    y + PdfTextStyle.SMALL.lineHeight + PdfTextStyle.HEADING.size,
                    ellipsize(kpi.value, PdfTextStyle.HEADING, maxWidth),
                    PdfTextStyle.HEADING,
                )
            }
            y += rowHeight
        }
        y += SECTION_GAP / 2
    }

    private fun paragraphs(texts: List<String>, color: Int) {
        if (texts.isEmpty()) return
        texts.forEach { lines(wrap(it, PdfTextStyle.BODY, spec.contentWidth), PdfTextStyle.BODY, color) }
        y += SECTION_GAP / 2
    }

    private fun bars(title: String, bars: List<PdfBar>, emptyMessage: String) {
        val rowHeight = 16f
        heading(title, rowHeight)
        if (bars.none { it.value > 0 }) {
            lines(listOf(emptyMessage), PdfTextStyle.BODY, PdfColors.MUTED)
            y += SECTION_GAP
            return
        }
        val labelWidth = spec.contentWidth * 0.32f
        val valueWidth = bars.maxOf { measurer.width(it.valueLabel, PdfTextStyle.BODY) } + 6f
        val barLeft = left + labelWidth + 6f
        val barMaxWidth = (right - valueWidth - barLeft).coerceAtLeast(10f)
        val maxValue = bars.maxOf { it.value }.coerceAtLeast(1)
        bars.forEach { bar ->
            ensureSpace(rowHeight)
            text(left, ellipsize(bar.label, PdfTextStyle.BODY, labelWidth), PdfTextStyle.BODY)
            val width = max(1f, barMaxWidth * bar.value.coerceAtLeast(0) / maxValue)
            ops += PdfOp.Rect(barLeft, y + 2f, barLeft + width, y + rowHeight - 5f, PdfColors.ACCENT)
            textEnd(right, bar.valueLabel, PdfTextStyle.BODY)
            y += rowHeight
        }
        y += SECTION_GAP
    }

    private fun timeline(title: String, points: List<PdfBar>, emptyMessage: String) {
        val chartHeight = 120f
        val small = PdfTextStyle.SMALL
        val axisLabels = small.lineHeight + 2f
        heading(title, chartHeight + axisLabels)
        if (points.none { it.value > 0 }) {
            lines(listOf(emptyMessage), PdfTextStyle.BODY, PdfColors.MUTED)
            y += SECTION_GAP
            return
        }
        val maxPoint = points.maxBy { it.value }
        val chartLeft = left + measurer.width(maxPoint.valueLabel, small) + 6f
        val chartTop = y
        val chartBottom = y + chartHeight
        ops += PdfOp.Line(chartLeft, chartTop, chartLeft, chartBottom)
        ops += PdfOp.Line(chartLeft, chartBottom, right, chartBottom)
        ops += PdfOp.Line(chartLeft, chartTop, right, chartTop, 0.3f)
        ops += PdfOp.Text(left, chartTop + small.size, maxPoint.valueLabel, small, PdfColors.MUTED)
        ops += PdfOp.Text(left, chartBottom, "0", small, PdfColors.MUTED)
        val step = if (points.size == 1) 0f else (right - chartLeft - 8f) / (points.size - 1)
        val coords = points.mapIndexed { index, point ->
            val x = if (points.size == 1) (chartLeft + right) / 2 else chartLeft + 4f + index * step
            x to chartBottom - (chartHeight - 6f) * point.value.coerceAtLeast(0) / maxPoint.value
        }
        if (coords.size > 1) ops += PdfOp.Polyline(coords)
        if (coords.size <= MAX_MARKERS) {
            coords.forEach { (x, py) -> ops += PdfOp.Rect(x - 1.5f, py - 1.5f, x + 1.5f, py + 1.5f, PdfColors.ACCENT) }
        }
        y = chartBottom + 2f
        val first = points.first().label
        text(coords.first().first.coerceAtMost(right - measurer.width(first, small)), first, small, PdfColors.MUTED)
        if (points.size > 1) textEnd(right, points.last().label, small, PdfColors.MUTED)
        y += axisLabels + SECTION_GAP
    }

    private fun table(title: String, table: PdfTable) {
        val padding = 3f
        val totalWeight = table.columns.sumOf { it.weight.toDouble() }.toFloat()
        val widths = table.columns.map { spec.contentWidth * it.weight / totalWeight }
        val starts = widths.runningFold(left) { acc, width -> acc + width }

        fun cellLines(cells: List<String>, style: PdfTextStyle) = cells.mapIndexed { index, cell ->
            val maxWidth = widths[index] - 2 * padding
            val wrapped = wrap(cell, style, maxWidth)
            if (wrapped.size <= MAX_CELL_LINES) {
                wrapped
            } else {
                wrapped.take(MAX_CELL_LINES - 1) + ellipsize(wrapped.drop(MAX_CELL_LINES - 1).joinToString(" "), style, maxWidth)
            }
        }

        fun rowHeight(cells: List<List<String>>, style: PdfTextStyle) =
            (cells.maxOfOrNull { it.size } ?: 1) * style.lineHeight + 2 * padding

        fun drawRow(cells: List<List<String>>, style: PdfTextStyle, height: Float, fill: Int?) {
            if (fill != null) ops += PdfOp.Rect(left, y, right, y + height, fill)
            cells.forEachIndexed { index, lines ->
                lines.forEachIndexed { lineIndex, line ->
                    val baseline = y + padding + lineIndex * style.lineHeight + style.size
                    val x = when (table.columns[index].align) {
                        PdfAlign.START -> starts[index] + padding
                        PdfAlign.END -> starts[index] + widths[index] - padding - measurer.width(line, style)
                    }
                    ops += PdfOp.Text(x, baseline, line, style)
                }
            }
            y += height
            ops += PdfOp.Line(left, y, right, y)
        }

        if (table.rows.isEmpty()) {
            heading(title, PdfTextStyle.BODY.lineHeight)
            lines(listOf(table.emptyMessage), PdfTextStyle.BODY, PdfColors.MUTED)
            return
        }
        val header = cellLines(table.columns.map { it.title }, PdfTextStyle.BODY_BOLD)
        val headerHeight = rowHeight(header, PdfTextStyle.BODY_BOLD)
        val firstRow = cellLines(table.rows.first(), PdfTextStyle.BODY)
        heading(title, headerHeight + rowHeight(firstRow, PdfTextStyle.BODY))
        drawRow(header, PdfTextStyle.BODY_BOLD, headerHeight, PdfColors.HEADER_FILL)
        table.rows.forEachIndexed { index, row ->
            val cells = if (index == 0) firstRow else cellLines(row, PdfTextStyle.BODY)
            val height = rowHeight(cells, PdfTextStyle.BODY)
            if (y + height > spec.contentBottom) {
                newPage()
                drawRow(header, PdfTextStyle.BODY_BOLD, headerHeight, PdfColors.HEADER_FILL)
            }
            drawRow(cells, PdfTextStyle.BODY, height, null)
        }
    }

    companion object {
        const val ELLIPSIS = "…"
        private const val SECTION_GAP = 14f
        private const val MAX_CELL_LINES = 4
        private const val MAX_MARKERS = 62
    }
}
