package com.pft.financetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.model.MoneyTone
import com.pft.financetracker.ui.model.accountLabel
import com.pft.financetracker.ui.model.signOf
import com.pft.financetracker.ui.model.toneOf
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.Neutral
import com.pft.financetracker.ui.theme.motion
import com.pft.financetracker.ui.theme.surfaces

/** Money colour by meaning: ink for spend (red is kept for warnings), green for money in, grey for money that only moved. */
@Composable
fun flowColor(t: Transaction): Color = toneColor(toneOf(t))

@Composable
fun toneColor(tone: MoneyTone): Color = when (tone) {
    MoneyTone.OUT -> MaterialTheme.colorScheme.onSurface
    MoneyTone.IN -> Income
    MoneyTone.MOVED -> Neutral
}

/** The tint for a category's icon and chart slice. */
fun categoryColor(c: Category): Color = colorFor(Category.entries.indexOf(c))

/** A category icon in its own tinted square: the leading element of every ledger row. */
@Composable
fun CategoryIcon(c: Category, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = RowIconSize) {
    val tint = categoryColor(c)
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(c), null, Modifier.size(size * 0.5f), tint = avatarInk(tint))
    }
}

/**
 * The one list row: a leading element, a title with a quiet line under it, and an amount column of fixed minimum
 * width so amounts line up down the list. Tap opens, long-press selects (when [onLongClick] is set). Selected rows
 * take the accent tint and a tick in place of the leading element.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerRow(
    title: String,
    subtitle: String?,
    amount: String,
    amountColor: Color,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    tag: String? = null,
    selected: Boolean = false,
    titleModifier: Modifier = Modifier,
    amountModifier: Modifier = Modifier,
    spoken: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) surfaces.accentSoft else surfaces.page.copy(alpha = 0f), motion(Motion.effects()), label = "rowBg")
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (spoken != null) Modifier.clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                this.selected = selected
                if (onLongClick != null) this.onLongClick(label = "Select") { onLongClick(); true }
            } else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Box(Modifier.size(RowIconSize), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        } else leading()
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f)) {
            Text(title, titleModifier, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.widthIn(min = LedgerAmountMinWidth), horizontalAlignment = Alignment.End) {
            LedgerAmount(amount, amountColor, amountModifier)
            if (tag != null) Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.End)
        }
    }
}

/**
 * A transaction as a [LedgerRow]: category icon, merchant, "Category · Bank ••1234", and the signed amount. [tag]
 * replaces the automatic flow tag ("Refunded", "Reversed"). The icon and amount are shared elements, so they grow
 * into the detail screen.
 */
@Composable
fun TransactionRow(
    t: Transaction,
    showDate: Boolean = true,
    tag: String? = null,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shown = tag ?: flowTag(t.flow)
        ?: (if (t.originalAmountPaise != null && t.type == TransactionType.DEBIT) "Your share" else null)
        ?: if (showDate) shortDate(t.timestamp) else null
    val amount = signOf(t) + money(t.amountPaise)
    val sub = listOfNotNull(t.category.label, accountLabel(t)).joinToString(" · ")
    LedgerRow(
        title = displayMerchant(t.merchant).ifBlank { t.category.label },
        subtitle = sub,
        amount = amount,
        amountColor = flowColor(t),
        modifier = modifier,
        leading = { CategoryIcon(t.category, Modifier.sharedElement(SharedKeys.icon(t.id))) },
        tag = shown,
        selected = selected,
        titleModifier = Modifier.sharedElement(SharedKeys.title(t.id)),
        amountModifier = Modifier.sharedElement(SharedKeys.amount(t.id)),
        spoken = listOfNotNull(displayMerchant(t.merchant), spokenAmount(t), sub, shown).joinToString(", "),
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/** "₹120 out", "₹45,000 in", "₹10,000 moved": direction in words, never colour alone. */
fun spokenAmount(t: Transaction): String = money(t.amountPaise) + when (toneOf(t)) {
    MoneyTone.OUT -> " out"
    MoneyTone.IN -> " in"
    MoneyTone.MOVED -> " moved"
}

/** One-word tags, short enough never to squeeze the line beside them. */
fun flowTag(f: Flow): String? = when (f) {
    Flow.EXPENSE, Flow.INCOME -> null
    Flow.REFUND -> "Refund"
    Flow.TRANSFER -> "Moved"
    Flow.INVESTMENT -> "Invested"
    Flow.CASH -> "Cash"
    Flow.SETTLEMENT -> "Settled"
}

/**
 * A day's header in a ledger: the day on the left ("Today", "Yesterday", "Mon 5 Oct"), its net on the right. Sticky in
 * Activity, on the page colour with a hairline under it.
 */
@Composable
fun DayHeader(day: String, date: String?, netPaise: Long, modifier: Modifier = Modifier) {
    val net = when {
        netPaise > 0 -> "+" + money(netPaise)
        netPaise < 0 -> money(netPaise)
        else -> null
    }
    Column(modifier.fillMaxWidth().background(surfaces.page)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.lg, bottom = Space.sm)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(day, style = MaterialTheme.typography.titleSmall)
            if (date != null) {
                Spacer(Modifier.width(6.dp))
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (net != null) Text(
                net, style = MoneyType.small, color = if (netPaise > 0) Income else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false,
                modifier = Modifier.semantics { contentDescription = if (netPaise > 0) "Net ${money(netPaise)} in" else "Net ${money(-netPaise)} out" },
            )
        }
        Hairline()
    }
}

/** A leading element for rows that are not transactions: an icon in a tinted square. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) = TintedSquare(icon, tint, RowIconSize)

