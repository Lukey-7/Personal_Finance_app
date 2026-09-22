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
fun TransactionRow(t: Transaction, onClick: () -> Unit) {
    val color = colorFor(Category.entries.indexOf(t.category))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LetterAvatar(t.merchant.ifBlank { t.category.label }, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.merchant,
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
            val tag = if (t.flow != Flow.EXPENSE && t.flow != Flow.INCOME) t.flow.label else shortDate(t.timestamp)
            Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
