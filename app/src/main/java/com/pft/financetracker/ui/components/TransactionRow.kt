package com.pft.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun TransactionRow(t: Transaction, onClick: () -> Unit) {
    val color = colorFor(Category.entries.indexOf(t.category))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Text(t.category.label.take(1), color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val flowTag = if (t.flow != Flow.EXPENSE && t.flow != Flow.INCOME) t.flow.label else null
            Text(
                listOfNotNull(flowTag, t.category.label, t.bankName, t.accountRef?.let { "••$it" }, shortDate(t.timestamp)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        val sign = if (t.type == TransactionType.CREDIT) "+" else "-"
        Text(
            sign + money(t.amountPaise),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = flowColor(t)
        )
    }
}
