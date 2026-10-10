package com.pft.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.model.QuickAddDraft
import com.pft.financetracker.ui.nav.AppNav
import com.pft.financetracker.ui.nav.Routes
import com.pft.financetracker.ui.theme.FinTrackTheme

class MainActivity : ComponentActivity() {
    /** A screen asked for from outside (the quick-add sheet's "More details"), opened once the app is showing. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingRoute.value = routeFrom(intent)
        val app = application as FinanceApp
        setContent {
            FinTrackTheme {
                var opened by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(app.container != null) }
                if (opened) AppNav(pendingRoute = pendingRoute.value, onRouteHandled = { pendingRoute.value = null })
                else com.pft.financetracker.ui.screens.KeyProblemScreen(
                    onRetry = { opened = app.start() },
                    onStartFresh = { opened = app.startFresh() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFrom(intent)?.let { pendingRoute.value = it }
    }

    private fun routeFrom(i: Intent?): String? {
        if (i?.getBooleanExtra(EXTRA_EDIT, false) != true) return null
        return Routes.edit(
            QuickAddDraft(
                amount = i.getStringExtra(EXTRA_AMOUNT).orEmpty(),
                category = i.getStringExtra(EXTRA_CATEGORY)?.let { Category.fromName(it) },
                note = i.getStringExtra(EXTRA_NOTE).orEmpty(),
                cash = i.getBooleanExtra(EXTRA_CASH, false),
            )
        )
    }

    companion object {
        private const val EXTRA_EDIT = "edit"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_NOTE = "note"
        private const val EXTRA_CASH = "cash"

        /** Opens the full editor filled in from a quick-add draft. */
        fun editIntent(context: Context, d: QuickAddDraft) = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_EDIT, true)
            .putExtra(EXTRA_AMOUNT, d.amount)
            .putExtra(EXTRA_CATEGORY, d.category?.name)
            .putExtra(EXTRA_NOTE, d.note)
            .putExtra(EXTRA_CASH, d.cash)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
