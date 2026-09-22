package com.pft.financetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.CategoryColors

data class Slice(val label: String, val value: Double, val color: Color, val category: com.pft.financetracker.domain.model.Category? = null)

fun colorFor(index: Int): Color = CategoryColors[index % CategoryColors.size]

/** Donut chart drawn with Canvas. No third-party chart library. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, centerText: String? = null) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val stroke = 28.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            var start = -90f
            if (slices.isEmpty()) {
                drawArc(Color.LightGray.copy(alpha = 0.4f), 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
            }
            slices.forEach { s ->
                val sweep = (s.value / total * 360f).toFloat()
                drawArc(s.color, start, sweep - 1f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
                start += sweep
            }
        }
        if (centerText != null) {
            Text(centerText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun Legend(slices: List<Slice>, modifier: Modifier = Modifier, onClick: ((com.pft.financetracker.domain.model.Category) -> Unit)? = null) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.forEach { s ->
            val rowMod = if (onClick != null && s.category != null) Modifier.clickable { onClick(s.category) } else Modifier
            Row(rowMod, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(s.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(s.value / total * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Text(money(s.value), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Vertical bar chart with labels; used for trends (weeks / months). */
@Composable
fun BarChart(bars: List<Pair<String, Double>>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, highlightLast: Boolean = true) {
    val max = bars.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bars.forEachIndexed { i, (_, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (v > 0) money(v) else "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val h = (v / max * 110).dp
                    val c = if (highlightLast && i == bars.lastIndex) color else color.copy(alpha = 0.45f)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(maxOf(h, 2.dp)).background(c, MaterialTheme.shapes.small))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bars.forEach { (label, _) ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
