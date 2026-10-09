package com.pft.financetracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.reducedMotion

/** How big a hero figure starts. It steps down a size (56 → 44 → 36 → 28) rather than wrap or cut. */
enum class AmountSize { Hero, Large, Medium, Title }

private fun AmountSize.ladder(): List<TextStyle> = when (this) {
    AmountSize.Hero -> listOf(MoneyType.hero, MoneyType.large, MoneyType.medium, MoneyType.title)
    AmountSize.Large -> listOf(MoneyType.large, MoneyType.medium, MoneyType.title)
    AmountSize.Medium -> listOf(MoneyType.medium, MoneyType.title, MoneyType.tile)
    AmountSize.Title -> listOf(MoneyType.title, MoneyType.tile, MoneyType.row)
}

/**
 * A money figure as the hero of its screen: the display face with tabular figures, the sign before a smaller, quieter
 * ₹ (-₹500, +₹45,000), never wrapped or cut. When the value changes its digits roll to the new one (up for more, down
 * for less); with animations off it simply changes. TalkBack reads the whole amount, plus [spokenLabel] if given.
 *
 * [prefix] is a sign to show before the ₹ ("+" for income, "-" for spend); a negative [paise] carries its own.
 */
@Composable
fun AmountDisplay(
    paise: Long,
    modifier: Modifier = Modifier,
    size: AmountSize = AmountSize.Hero,
    color: Color = MaterialTheme.colorScheme.onBackground,
    prefix: String = "",
    estimate: Boolean = false,
    spokenLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val full = prefix + if (estimate) approxMoney(paise) else money(paise)
    // Split "-₹1,05,000" into sign, rupee mark and digits, so the ₹ can sit smaller and quieter.
    val r = full.indexOf('₹')
    val sign = if (r > 0) full.substring(0, r) else ""
    val digits = if (r >= 0) full.substring(r + 1) else full
    // Which way the digits roll, decided once per new value (not a state write, so it never recomposes twice).
    val direction = remember { RollDirection(paise) }
    val up = direction.upTo(paise)

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(spokenLabel, full).joinToString(": ")
                if (onClick != null) role = Role.Button
            },
    ) {
        val maxPx = with(density) { maxWidth.toPx() }
        val style = size.ladder().firstOrNull { s ->
            measurer.measure(full, s, softWrap = false, maxLines = 1).size.width <= maxPx
        } ?: size.ladder().last().let { s ->
            // Even the smallest step is too wide (crores in a narrow tile): shrink it to fit rather than cut it.
            val w = measurer.measure(full, s, softWrap = false, maxLines = 1).size.width
            if (w <= 0 || maxPx <= 0f) s else s.copy(fontSize = s.fontSize * (maxPx / w * 0.98f).coerceIn(0.5f, 1f))
        }
        val small = style.copy(fontSize = style.fontSize * 0.62f, letterSpacing = style.letterSpacing * 0.5f, baselineShift = androidx.compose.ui.text.style.BaselineShift(0.52f))
        Row(verticalAlignment = Alignment.Bottom) {
            if (sign.isNotEmpty()) Text(sign, Modifier.alignByBaseline(), style = style, color = color, maxLines = 1, softWrap = false)
            if (r >= 0) Text(
                "₹", style = small, color = color.copy(alpha = 0.62f), maxLines = 1, softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            RollingText(digits, style, color, up, Modifier.alignByBaseline())
        }
    }
}

/**
 * Text whose characters roll to their new values: each position (counted from the right, so units stay units) slides
 * up when the number grows and down when it shrinks. Plain text when animations are off.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, up: Boolean = true, modifier: Modifier = Modifier) {
    val memo = remember { RollMemo(text) }
    val sameShape = memo.sameShapeAs(text)
    if (reducedMotion) {
        Text(text, modifier, style = style, color = color, maxLines = 1, softWrap = false)
        return
    }
    // Digits roll only when the commas and the point stay where they were (12,345 -> 12,890). When the layout
    // changes (1,23,456 -> 1,234.50) rolling each slot would show junk mid-way, so the whole figure fades instead.
    if (!sameShape) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects()) },
            modifier = modifier,
            label = "figure",
        ) { t -> Text(t, style = style, color = color, maxLines = 1, softWrap = false) }
        return
    }
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = { rollTransition(up) },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color, maxLines = 1, softWrap = false) }
            }
        }
    }
}

/** Remembers the last value so a new one knows whether it went up or down. */
internal class RollDirection(private var last: Long) {
    private var up = true
    fun upTo(value: Long): Boolean {
        if (value != last) { up = value > last; last = value }
        return up
    }
}

/** Remembers the last text's layout of digits and separators; answers whether a new text keeps it. */
internal class RollMemo(private var lastText: String) {
    private var lastMask = rollMask(lastText)
    private var same = true
    fun sameShapeAs(text: String): Boolean {
        if (text != lastText) {
            val m = rollMask(text)
            same = m == lastMask
            lastMask = m
            lastText = text
        }
        return same
    }
}

/** "1,23,456" -> "0,00,000": where the digits and the separators sit. */
internal fun rollMask(text: String): String = buildString(text.length) { text.forEach { append(if (it.isDigit()) '0' else it) } }

private fun rollTransition(up: Boolean): ContentTransform {
    val spec: FiniteAnimationSpec<IntOffset> = Motion.roll()
    return ContentTransform(
        targetContentEnter = slideInVertically(spec) { h -> if (up) h else -h } + fadeIn(Motion.effects()),
        initialContentExit = slideOutVertically(spec) { h -> if (up) -h else h } + fadeOut(Motion.effects()),
        sizeTransform = SizeTransform(clip = true),
    )
}

/**
 * An amount in a list's right-hand column: display face, tabular figures, one line, never cut. Pair with a fixed
 * minimum width (see [LedgerRow]) so a column of these lines up.
 */
@Composable
fun LedgerAmount(text: String, color: Color, modifier: Modifier = Modifier, style: TextStyle = MoneyType.row) {
    Text(text, modifier, style = style, color = color, maxLines = 1, softWrap = false)
}

/** Minimum width of a list's amount column at the current font size: room for "-₹1,05,000" in [MoneyType.row]. */
val LedgerAmountMinWidth = 88.dp
