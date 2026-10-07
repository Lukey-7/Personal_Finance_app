package com.pft.financetracker.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.theme.FinTrackTheme
import com.pft.financetracker.ui.theme.Income

/** Every component in light, dark and at the largest system font (200%), at the emulator's 393dp width. */
@Preview(name = "Light", widthDp = 393, showBackground = true)
@Preview(name = "Dark", widthDp = 393, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "200%", widthDp = 393, showBackground = true, fontScale = 2f)
annotation class FinPreviews

@Composable
private fun Frame(content: @Composable () -> Unit) = FinTrackTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) { content() }
}

private fun sample(id: Long, merchant: String, paise: Long, cat: Category, flow: Flow = Flow.EXPENSE, type: TransactionType = TransactionType.DEBIT) =
    Transaction(id = id, amountPaise = paise, type = type, merchant = merchant, category = cat, timestamp = 1_791_000_000_000L,
        bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = flow)

@FinPreviews @Composable
private fun AmountDisplayPreview() = Frame {
    Column(Modifier.padding(horizontal = Gutter)) {
        CapsLabel("Net spend · Oct 2026")
        AmountDisplay(4_218_00)
        AmountDisplay(1_05_000_00, size = AmountSize.Medium, color = Income, prefix = "+")
        AmountDisplay(-500_00, size = AmountSize.Title)
    }
}

@FinPreviews @Composable
private fun LedgerPreview() = Frame {
    Column {
        DayHeader("Today", "7 Oct", -920_00)
        TransactionRow(sample(1, "Zomato", 120_00, Category.FOOD)) {}
        Hairline(startInset = RowTextInset, endInset = Gutter)
        TransactionRow(sample(2, "Acme Ltd", 45_000_00, Category.INCOME, Flow.INCOME, TransactionType.CREDIT), selected = true) {}
        Hairline(startInset = RowTextInset, endInset = Gutter)
        TransactionRow(sample(3, "To own account", 10_000_00, Category.TRANSFER, Flow.TRANSFER)) {}
    }
}

@FinPreviews @Composable
private fun ControlsPreview() = Frame {
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        SegmentedControl(listOf("This month", "Last month", "This week"), 0, {}, trailing = Icons.Outlined.DateRange, trailingLabel = "Custom range", onTrailing = {})
        SegmentedControl(listOf("Monthly", "Weekly"), 1, {})
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            FilterButton(2, {})
            AppliedTag("Food & Dining") {}
        }
        ChipFlow { Category.entries.take(5).forEachIndexed { i, c -> PillChip(i == 1, c.label, icon = categoryIcon(c)) {} } }
        PrimaryButton("Save ₹340", {})
        Row { SecondaryButton("Import", {}); TextAction("Learn more", {}) }
        AddButton({}, expanded = true)
    }
}

@FinPreviews @Composable
private fun NumberPadPreview() = Frame {
    Column(Modifier.padding(horizontal = Gutter)) { NumberPad({ true }, {}, {}) }
}

@FinPreviews @Composable
private fun ChartsPreview() = Frame {
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
        SpendChart(
            listOf(ChartBar("May", 12_000_00), ChartBar("Jun", 15_800_00), ChartBar("Jul", 9_900_00), ChartBar("Aug", 21_000_00), ChartBar("Sep", 19_940_00), ChartBar("Oct", 4_218_00, current = true)),
            selected = 4,
        )
        CategoryRing(
            listOf(Slice("Shopping", 1_299.0, colorFor(1), Category.SHOPPING), Slice("Food & Dining", 920.0, colorFor(0), Category.FOOD), Slice("Bills", 899.0, colorFor(2), Category.BILLS)),
            "₹3,118", "5 payments",
        )
        ProgressMeter(0.62f)
        ProgressMeter(1f, over = true)
    }
}

@FinPreviews @Composable
private fun StatesPreview() = Frame {
    SkeletonHero(cards = 1)
    SkeletonRows(2)
    EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "No transactions yet", "Scan your SMS from Home, or add one yourself.") { PrimaryButton("Add a transaction", {}, fill = false) }
    Column(Modifier.padding(horizontal = Gutter)) {
        ExpandableCard("Budgets", "1 over · Food ₹320 over", Icons.Outlined.Savings, initiallyExpanded = true) {
            Text("Food & Dining", style = MaterialTheme.typography.bodyMedium)
            ProgressMeter(1f, over = true)
        }
    }
}
