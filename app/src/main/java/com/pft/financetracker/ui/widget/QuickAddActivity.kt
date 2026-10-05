package com.pft.financetracker.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pft.financetracker.BuildConfig
import com.pft.financetracker.appContainer
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.theme.FinTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A small sheet over the home screen to note a purchase in seconds: amount, category, done. Opened by the widget or a shortcut. */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val cash = intent.getBooleanExtra(EXTRA_CASH, false)
        val cashCounted = appContainer.settings.countCashAsSpend.value
        setContent { FinTrackTheme { Sheet(cash, cashCounted, onSave = ::save, onCancel = ::finish) } }
    }

    private fun save(t: Transaction) = lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            appContainer.transactions.insert(t)
            appContainer.afterChange(useAi = false)
        }
        FinTrackWidget.refresh(applicationContext)
        finish()
    }

    companion object {
        const val EXTRA_CASH = "cash"
        fun intent(context: Context, cash: Boolean) = Intent(context, QuickAddActivity::class.java)
            .putExtra(EXTRA_CASH, cash).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

@Composable
private fun Sheet(startCash: Boolean, cashCounted: Boolean, onSave: (Transaction) -> Unit, onCancel: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.FOOD) }
    var note by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf(startCash) }
    val paise = Money.parsePaise(amount)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (cash) "Paid in cash" else "Add an expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                ChipRow(inset = 0.dp) { Category.spendCategories.filter { it != Category.TRANSFER && it != Category.INVESTMENT }.forEach { c -> PillChip(category == c, c.label) { category = c } } }
                OutlinedTextField(note, { note = it }, label = { Text("What for (optional)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Paid in cash", Modifier.weight(1f)); Switch(cash, { cash = it })
                }
                // ATM withdrawals already count as spend when that setting is on, so cash purchases would count twice.
                if (cash && cashCounted) Text(
                    "ATM withdrawals already count as your spend (Settings). Add cash purchases only to see where the cash went; they are kept out of totals.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = paise != null, onClick = {
                onSave(
                    Transaction(
                        amountPaise = paise!!, type = TransactionType.DEBIT, merchant = note.trim().ifBlank { category.label }, category = category,
                        timestamp = System.currentTimeMillis(), bankName = if (cash) "Cash" else null, accountRef = null, source = Transaction.Source.MANUAL,
                        // A cash purchase already paid for by a counted ATM withdrawal is a transfer within your own money, not new spend.
                        flow = if (cash && cashCounted) Flow.TRANSFER else Flow.EXPENSE, note = if (cash) "Paid in cash" else null,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
