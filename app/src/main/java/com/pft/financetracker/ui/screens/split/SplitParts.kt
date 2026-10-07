package com.pft.financetracker.ui.screens.split

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.components.CardPadding
import com.pft.financetracker.ui.components.CardRadius
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.LetterAvatar
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.theme.surfaces

/*
 * Pieces the three split screens share: a person's avatar, the receipt card with its perforated foot, the dashed tear
 * line inside it, and the titled group a form section sits in.
 */

/** A stable tint per name, so the same friend wears the same colour on every screen. */
internal fun personTint(name: String): Color = colorFor(name.trim().lowercase().hashCode() and Int.MAX_VALUE)

/** A person as a letter avatar (contrast-checked initial on a soft wash of their tint). */
@Composable
internal fun PersonAvatar(name: String, size: Dp = 36.dp) {
    LetterAvatar(name.ifBlank { "?" }, personTint(name), size)
}

/** Depth of each notch in a receipt's perforated foot, and the distance between notches. */
private val NotchRadius = 4.dp
private val NotchPitch = 14.dp

/**
 * A card with rounded top corners and a row of small half-circle notches bitten out of its bottom edge, like a receipt
 * torn off a roll. The notches sit inside the bounds, so content keeps its padding clear of them.
 */
private class ReceiptShape(private val corner: Dp, private val notch: Dp, private val pitch: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { corner.toPx() }.coerceAtMost(minOf(w, h) / 2f)
        val n = with(density) { notch.toPx() }
        val step = with(density) { pitch.toPx() }
        val count = (w / step).toInt().coerceAtLeast(1)
        val gap = w / count
        val path = Path().apply {
            moveTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)
            lineTo(w, h)
            // Right to left along the foot: a half-circle bite at the middle of each slot.
            for (i in count - 1 downTo 0) {
                val cx = gap * (i + 0.5f)
                lineTo(cx + n, h)
                arcTo(Rect(cx - n, h - n, cx + n, h + n), 0f, -180f, false)
            }
            lineTo(0f, h)
            close()
        }
        return Outline.Generic(path)
    }
}

private val Receipt: Shape = ReceiptShape(CardRadius, NotchRadius, NotchPitch)

/**
 * A split as a receipt: card surface on a hairline, rounded at the top, perforated along the bottom. Tappable when
 * [onClick] is set (TalkBack hears [onClickLabel]).
 */
@Composable
internal fun ReceiptCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: PaddingValues = PaddingValues(start = CardPadding, end = CardPadding, top = Space.lg, bottom = Space.lg + NotchRadius),
    spacing: Dp = Space.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = surfaces
    Column(
        modifier
            .fillMaxWidth()
            .clip(Receipt)
            .background(s.card)
            .border(1.dp, s.hairline, Receipt)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** The dashed line across a receipt between what it was and what it came to. Decorative. */
@Composable
internal fun TearLine(modifier: Modifier = Modifier) {
    val color = surfaces.outline.copy(alpha = 0.55f)
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        val dash = 4.dp.toPx()
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f),
        )
    }
}

/** A titled group on a form page: a card with a heading and an optional quiet line under it. */
@Composable
internal fun FormSection(title: String, subtitle: String? = null, raised: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    FinCard(raised = raised, spacing = Space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}
