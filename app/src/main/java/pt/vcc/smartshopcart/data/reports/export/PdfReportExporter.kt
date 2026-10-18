package pt.vcc.smartshopcart.data.reports.export

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfLayoutEngine
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfOp
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfPageSpec
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfReportContentBuilder
import pt.vcc.smartshopcart.data.reports.export.pdf.PdfTextStyle
import pt.vcc.smartshopcart.domain.reports.ReportData
import java.io.OutputStream
import javax.inject.Inject

/**
 * PDF A4 gerado no dispositivo com [PdfDocument]: o [PdfLayoutEngine] calcula a paginação e aqui só
 * se desenha (texto, linhas, barras e linha temporal vetoriais). Ignora a granularidade: o PDF
 * inclui sempre o resumo, os gráficos e a tabela de artigos.
 */
class PdfReportExporter @Inject constructor() : ReportExporter {
    override val format = ExportFormat.PDF

    override suspend fun write(report: ReportData, output: OutputStream, options: ExportOptions) =
        withContext(Dispatchers.Default) {
            val spec = PdfPageSpec.A4
            val paints = PdfPaints()
            val pages = PdfLayoutEngine(paints::measure, spec).layout(PdfReportContentBuilder.build(report))
            val document = PdfDocument()
            try {
                pages.forEach { pdfPage ->
                    ensureActive()
                    val info = PdfDocument.PageInfo.Builder(spec.width.toInt(), spec.height.toInt(), pdfPage.number).create()
                    val page = document.startPage(info)
                    pdfPage.ops.forEach { paints.draw(page.canvas, it) }
                    document.finishPage(page)
                }
                ensureActive()
                withContext(Dispatchers.IO) { document.writeTo(output) }
            } finally {
                document.close()
            }
        }

    private class PdfPaints {
        private val text = PdfTextStyle.entries.associateWith { style ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = style.size
                typeface = if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        fun measure(value: String, style: PdfTextStyle): Float = text.getValue(style).measureText(value)

        fun draw(canvas: android.graphics.Canvas, op: PdfOp) {
            when (op) {
                is PdfOp.Text -> {
                    val paint = text.getValue(op.style)
                    paint.color = op.color
                    canvas.drawText(op.text, op.x, op.baseline, paint)
                }
                is PdfOp.Line -> {
                    stroke.color = op.color
                    stroke.strokeWidth = op.width
                    canvas.drawLine(op.x1, op.y1, op.x2, op.y2, stroke)
                }
                is PdfOp.Rect -> {
                    fill.color = op.color
                    canvas.drawRect(op.left, op.top, op.right, op.bottom, fill)
                }
                is PdfOp.Polyline -> {
                    stroke.color = op.color
                    stroke.strokeWidth = op.width
                    val path = Path()
                    op.points.forEachIndexed { index, (x, y) -> if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    canvas.drawPath(path, stroke)
                }
            }
        }
    }
}
