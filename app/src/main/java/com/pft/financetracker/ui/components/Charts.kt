package com.pft.financetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.model.ChartMath
import com.pft.financetracker.ui.theme.CategoryColors
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.reducedMotion
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.surfaces
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One chart slice: a category's share. */
data class Slice(val label: String, val value: Double, val color: Color, val category: Category? = null)

fun colorFor(index: Int): Color = CategoryColors[index % CategoryColors.size]

/** One bar: a period's label and its figure. [current] is drawn in the accent (the period still running). */
data class ChartBar(val label: String, val paise: Long, val current: Boolean = false)

/**
 * A spend chart you can read by touch: tap a bar, or drag across, to see its value above the chart (a tick at each
 * new bar). Every bar owns a full-height column of at least 44dp, so it is easy to hit, and each is a TalkBack button
 * that speaks "Sep: ₹19,940". [onOpen] runs on a second tap of the selected bar (Home opens that period).
 * [compact] drops the value line for a spark chart under a hero figure.
 */
@Composable
fun SpendChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    onOpen: ((Int) -> Unit)? = null,
    compact: Boolean = false,
    caption: ((Int) -> String)? = null,
) {
    val haptics = rememberHaptics()
    val fractions = ChartMath.fractions(bars.map { it.paise })
    val accent = MaterialTheme.colorScheme.primary
    val rest = surfaces.outline
    val base = surfaces.hairline
    val rm = reducedMotion
    val grow = remember { Animatable(if (rm) 1f else 0f) }
    LaunchedEffect(bars.size) { if (grow.value < 1f) grow.animateTo(1f, Motion.spatial()) }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentOpen by rememberUpdatedState(onOpen)
    val currentSelected by rememberUpdatedState(selected)

    Column(modifier) {
        // The readout: the touched bar (or, on a full chart, the latest one) in words and figures. A spark chart
        // keeps the line's height reserved so touching a bar never shifts the page.
        val shown = selected ?: if (compact) null else bars.indexOfLast { it.current }.takeIf { it >= 0 } ?: bars.lastIndex
        Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 36.dp else 28.dp), verticalAlignment = Alignment.CenterVertically) {
            if (shown != null && shown in bars.indices) {
                Text(caption?.invoke(shown) ?: bars[shown].label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(Space.sm))
                Text(money(bars[shown].paise.coerceAtLeast(0)), style = MoneyType.tile, maxLines = 1, softWrap = false)
                if (compact && onOpen != null && !bars[shown].current) {
                    Spacer(Modifier.weight(1f))
                    TextAction("Show", { onOpen(shown) })
                }
            } else if (compact) Text("Tap a bar to read it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(if (compact) Space.xs else Space.sm))
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(bars.size) {
                        detectTapGestures { o ->
                            val i = ChartMath.indexAt(o.x, size.width.toFloat(), bars.size) ?: return@detectTapGestures
                            if (i == currentSelected && currentOpen != null) currentOpen?.invoke(i)
                            else { haptics.tick(); currentSelect(i) }
                        }
                    }
                    .pointerInput(bars.size) {
                        var last = -1
                        detectHorizontalDragGestures(onDragEnd = { last = -1 }) { change, _ ->
                            val i = ChartMath.indexAt(change.position.x, size.width.toFloat(), bars.size) ?: return@detectHorizontalDragGestures
                            if (i != last) { last = i; haptics.tick(); currentSelect(i) }
                        }
                    }
            ) {
                if (bars.isEmpty()) return@Canvas
                val col = size.width / bars.size
                val barW = (col * 0.56f).coerceAtMost(40.dp.toPx())
                val r = CornerRadius(minOf(barW / 2, 8.dp.toPx()))
                drawLine(base, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
                bars.forEachIndexed { i, b ->
                    val f = fractions[i] * grow.value
                    val h = (size.height - 2.dp.toPx()) * f
                    val left = col * i + (col - barW) / 2
                    val isSel = selected == i
                    val color = if (b.current || isSel) accent else rest
                    if (f > 0f) clipRect(top = size.height - h - r.x, bottom = size.height) {
                        drawRoundRect(color, Offset(left, size.height - h), Size(barW, h + r.x), r)
                    }
                    if (isSel && !b.current) drawRoundRect(accent, Offset(left - 3.dp.toPx(), size.height - h - 3.dp.toPx()), Size(barW + 6.dp.toPx(), h + 3.dp.toPx()), r, style = Stroke(1.5.dp.toPx()))
                }
            }
            // One invisible column per bar for TalkBack: no touch handling, so the gestures above stay in charge.
            Row(Modifier.fillMaxSize()) {
                bars.forEachIndexed { i, b ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().widthIn(min = 44.dp).clearAndSetSemantics {
                            contentDescription = ChartMath.spoken(b.label, b.paise)
                            role = Role.Button
                            this.selected = selected == i
                            onClick { if (selected == i && currentOpen != null) currentOpen?.invoke(i) else currentSelect(i); true }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            bars.forEachIndexed { i, b ->
                Text(
                    b.label, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.cappedScale(1.3f),
                    color = if (b.current || selected == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (b.current || selected == i) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * Where the money went: a ring of categories, tap a slice (or its row below) to open its payments. The centre shows the
 * total and a count, capped in size so it fits at the largest font. Each row carries the share in words, so nothing
 * depends on telling colours apart.
 */
@Composable
fun CategoryRing(
    slices: List<Slice>,
    centreValue: String,
    centreLabel: String,
    modifier: Modifier = Modifier,
    onOpen: ((Category) -> Unit)? = null,
) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    val empty = surfaces.sunken
    val ring = surfaces.card
    val rm = reducedMotion
    val grow = remember { Animatable(if (rm) 1f else 0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.spatial()) }
    val open by rememberUpdatedState(onOpen)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(184.dp)
                    .clearAndSetSemantics { }
                    .pointerInput(slices) {
                        detectTapGestures { o ->
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val d = o - c
                            val dist = sqrt(d.x * d.x + d.y * d.y)
                            if (dist < minOf(size.width, size.height) * 0.28f || dist > minOf(size.width, size.height) * 0.52f) return@detectTapGestures
                            var deg = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())) + 90.0
                            if (deg < 0) deg += 360.0
                            var acc = 0.0
                            slices.forEach { s ->
                                acc += s.value / total * 360.0
                                if (deg <= acc) { s.category?.let { cat -> open?.invoke(cat) }; return@detectTapGestures }
                            }
                        }
                    }
            ) {
                val stroke = 26.dp.toPx()
                val d = size.minDimension - stroke
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                if (slices.isEmpty()) drawArc(empty, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                var start = -90f
                slices.forEach { s ->
                    val sweep = (s.value / total * 360f).toFloat() * grow.value
                    drawArc(s.color, start, sweep, false, tl, Size(d, d), style = Stroke(stroke))
                    // A thin gap in the card colour keeps neighbouring slices apart without relying on hue.
                    drawArc(ring, start + sweep - 0.8f, 1.6f, false, tl, Size(d, d), style = Stroke(stroke + 2f, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 120.dp)) {
                Text(centreValue, style = MoneyType.title.cappedScale(1.15f), maxLines = 1, softWrap = false)
                Text(centreLabel, style = MaterialTheme.typography.labelMedium.cappedScale(1.15f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
        Column {
            slices.forEach { s ->
                val pct = (s.value / total * 100).roundToInt()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .then(if (onOpen != null && s.category != null) Modifier.clickable(role = Role.Button, onClickLabel = "See payments") { onOpen(s.category) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = "${s.label}, $pct percent, ${money(s.value)}" }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (s.category != null) CategoryIcon(s.category, size = 32.dp)
                    else Box(Modifier.padding(horizontal = 11.dp).size(10.dp))
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        ProgressMeter(s.value.toFloat() / total.toFloat(), color = s.color, height = 4.dp)
                    }
                    Spacer(Modifier.width(Space.md))
                    Text("$pct%", style = MoneyType.label, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, softWrap = false, modifier = Modifier.widthIn(min = 40.dp))
                    Text(money(s.value), style = MoneyType.small.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.End, softWrap = false, modifier = Modifier.widthIn(min = 76.dp))
                }
            }
        }
    }
}

/**
 * A bar for budgets, goals and shares. Over the limit it turns red and carries diagonal stripes, so "over" never
 * depends on colour alone (the caller also says it in words).
 */
@Composable
fun ProgressMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    over: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp,
) {
    val track = surfaces.sunken
    val fill = if (over) Expense else color
    val rm = reducedMotion
    val f = remember { Animatable(if (rm) fraction.coerceIn(0f, 1f) else 0f) }
    LaunchedEffect(fraction) { f.animateTo(fraction.coerceIn(0f, 1f), Motion.spatial()) }
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * f.value
        if (w > 0f) drawRoundRect(fill, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
        if (over) clipRect(right = w) {
            val step = 6.dp.toPx()
            var x = -size.height
            while (x < w + size.height) {
                drawLine(Color.White.copy(alpha = 0.45f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                x += step
            }
        }
    }
}
