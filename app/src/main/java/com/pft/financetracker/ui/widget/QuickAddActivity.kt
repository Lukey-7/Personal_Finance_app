package com.pft.financetracker.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.lifecycleScope
import com.pft.financetracker.MainActivity
import com.pft.financetracker.appContainer
import com.pft.financetracker.appContainerOrNull
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.ui.model.QuickAddDraft
import com.pft.financetracker.ui.screens.quickadd.QuickAddSheet
import com.pft.financetracker.ui.theme.FinTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The quick-add sheet over the home screen: amount, category, done. Opened by the widget or an app shortcut. "More
 * details" hands the draft to the app's full editor.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The data could not be opened: the app explains why, instead of a sheet that can't save.
        if (appContainerOrNull == null) {
            startActivity(android.content.Intent(this, com.pft.financetracker.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        val cash = intent.getBooleanExtra(EXTRA_CASH, false)
        val cashCounted = appContainer.settings.countCashAsSpend.value
        setContent {
            FinTrackTheme {
                // Only for ordering the category grid; the sheet works before it arrives.
                val recent by produceState(emptyList<Transaction>()) {
                    value = withContext(Dispatchers.IO) { runCatching { appContainer.db.transactionDao().getAll().map { it.toDomain() } }.getOrDefault(emptyList()) }
                }
                QuickAddSheet(recent, cashCounted, startCash = cash, onSave = ::save, onMoreDetails = ::moreDetails, onDismiss = ::finish)
            }
        }
    }

    /** Set by the first Save or "More details", so neither can run twice. */
    private var done = false

    private fun save(t: Transaction) {
        if (done) return
        done = true
        lifecycleScope.launch { insert(t) }
    }

    private suspend fun insert(t: Transaction) {
        withContext(Dispatchers.IO) {
            // The ledger's follow-up refreshes refunds and splits; the widget is refreshed here so it is current before the
            // sheet closes (the process may be frozen once nothing is on screen).
            appContainer.ledger.add(t)
        }
        FinTrackWidget.refresh(applicationContext)
        finish()
    }

    private fun moreDetails(d: QuickAddDraft) {
        if (done) return
        done = true
        startActivity(MainActivity.editIntent(this, d))
        finish()
    }

    companion object {
        const val EXTRA_CASH = "cash"
        fun intent(context: Context, cash: Boolean) = Intent(context, QuickAddActivity::class.java)
            .putExtra(EXTRA_CASH, cash).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
