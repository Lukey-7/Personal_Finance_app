package com.pft.financetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.Neutral

/** Money colour by meaning: red = spend, green = income/refund, grey = moved between your own pockets. */
fun flowColor(t: Transaction) = when (t.flow) {
    Flow.EXPENSE, Flow.CASH -> Expense
    Flow.INCOME, Flow.REFUND -> Income
    Flow.TRANSFER, Flow.INVESTMENT, Flow.SETTLEMENT -> Neutral
}

/**
 * A list row in the reference's shape: tinted circular initial, name in medium weight, quiet metadata
 * underneath, and the amount right-aligned with the secondary line below it.
 */
@Composable
fun TransactionRow(t: Transaction, showDate: Boolean = true, onClick: () -> Unit) {
    val color = colorFor(Category.entries.indexOf(t.category))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(t.merchant.ifBlank { t.category.label }, color, size = RowIconSize)
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f)) {
            Text(
                displayMerchant(t.merchant),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(t.category.label, t.bankName, t.accountRef?.let { "••$it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (t.type == TransactionType.CREDIT) "+" else "-"
            Text(
                sign + money(t.amountPaise),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = flowColor(t),
                textAlign = TextAlign.End,
            )
            // A short flow tag only where the colour alone doesn't say it; the date only where the list
            // has no date headers of its own (Activity groups by day, so it would just repeat).
            val tag = flowTag(t.flow) ?: if (showDate) shortDate(t.timestamp) else null
            if (tag != null) Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** One-word tags, short enough never to squeeze the merchant/bank line beside them. */
private fun flowTag(f: Flow): String? = when (f) {
    Flow.EXPENSE, Flow.INCOME -> null
    Flow.REFUND -> "Refund"
    Flow.TRANSFER -> "Transfer"
    Flow.INVESTMENT -> "Invested"
    Flow.CASH -> "Cash"
    Flow.SETTLEMENT -> "Settled"
}
