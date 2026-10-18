package pt.vcc.smartshopcart.feature.reports.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Gráficos simples desenhados em `Canvas`, independentes do modelo de relatório: recebem valores
 * já agregados (cêntimos) e uma descrição textual para leitores de ecrã.
 */
data class ChartValue(val label: String, val value: Long)

data class ChartGroupValue(val label: String, val first: Long, val second: Long)

val ChartPalette = listOf(
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFFEF6C00),
    Color(0xFF6A1B9A),
    Color(0xFFC62828),
    Color(0xFF00838F),
    Color(0xFF9E9D24),
    Color(0xFF5D4037),
    Color(0xFF546E7A),
)

fun chartColor(index: Int): Color = ChartPalette[index % ChartPalette.size]

/** Linha temporal com área preenchida e etiquetas no início, meio e fim. */
@Composable
fun LineChart(values: List<ChartValue>, description: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
        val chartBottom = size.height - labelHeight - 6.dp.toPx()
        val top = 8.dp.toPx()
        val max = values.maxOf { it.value }.coerceAtLeast(1)
        val step = if (values.size == 1) 0f else size.width / (values.size - 1)
        fun point(index: Int) = Offset(
            x = if (values.size == 1) size.width / 2 else index * step,
            y = chartBottom - (values[index].value.toFloat() / max) * (chartBottom - top),
        )

        drawLine(axis, Offset(0f, chartBottom), Offset(size.width, chartBottom), strokeWidth = 1.dp.toPx())
        val line = Path().apply {
            values.indices.forEach { index -> point(index).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(point(values.lastIndex).x, chartBottom)
            lineTo(point(0).x, chartBottom)
            close()
        }
        drawPath(area, color.copy(alpha = 0.15f))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        if (values.size <= 40) {
            values.indices.forEach { drawCircle(color, radius = 3.dp.toPx(), center = point(it)) }
        }

        listOf(0, values.lastIndex / 2, values.lastIndex).distinct().forEach { index ->
            val layout = measurer.measure(values[index].label, labelStyle)
            val x = (point(index).x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, chartBottom + 4.dp.toPx()))
        }
    }
}

/** Gráfico circular em anel; o texto central costuma ser o total. */
@Composable
fun DonutChart(values: List<ChartValue>, description: String, centerText: String, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val centerStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .size(180.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val stroke = 28.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        val total = values.sumOf { it.value }
        if (total <= 0) {
            drawArc(empty, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        } else {
            var start = -90f
            values.forEachIndexed { index, value ->
                val sweep = 360f * value.value / total
                drawArc(chartColor(index), start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        val layout = measurer.measure(centerText, centerStyle)
        drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, (size.height - layout.size.height) / 2))
    }
}

/** Legenda com a cor de cada série, para o gráfico circular e as barras agrupadas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(labels: List<String>, modifier: Modifier = Modifier, colors: List<Color> = labels.indices.map(::chartColor)) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(colors[index])
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

/** Barras horizontais (top N) com etiqueta e valor formatado; cada linha é lida como texto. */
@Composable
fun HorizontalBarChart(values: List<ChartValue>, formatValue: (Long) -> String, modifier: Modifier = Modifier) {
    val max = values.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { index, value ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        value.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatValue(value.value), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
                val fraction = value.value.toFloat() / max
                val color = chartColor(index)
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = radius)
                    drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                }
            }
        }
    }
}

/** Pares de barras verticais (ex.: orçamento vs. realizado). */
@Composable
fun GroupedBarChart(
    groups: List<ChartGroupValue>,
    firstLabel: String,
    secondLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val firstColor = MaterialTheme.colorScheme.outline
    val secondColor = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            if (groups.isEmpty()) return@Canvas
            val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
            val bottom = size.height - labelHeight - 6.dp.toPx()
            val max = groups.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1)
            val slot = size.width / groups.size
            val barWidth = (slot * 0.3f).coerceAtMost(28.dp.toPx())
            drawLine(axis, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
            groups.forEachIndexed { index, group ->
                val center = slot * index + slot / 2
                listOf(group.first to firstColor, group.second to secondColor).forEachIndexed { bar, (value, color) ->
                    val height = (value.toFloat() / max) * (bottom - 8.dp.toPx())
                    val left = if (bar == 0) center - barWidth - 1.dp.toPx() else center + 1.dp.toPx()
                    drawRoundRect(
                        color,
                        topLeft = Offset(left, bottom - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
                val layout = measurer.measure(group.label, labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                val x = (center - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                drawText(layout, topLeft = Offset(x, bottom + 4.dp.toPx()))
            }
        }
        ChartLegend(listOf(firstLabel, secondLabel), colors = listOf(firstColor, secondColor))
    }
}
