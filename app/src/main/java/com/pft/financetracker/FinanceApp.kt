package com.pft.financetracker

import android.app.Application
import android.content.Context
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.BudgetRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.parser.SmsParser

/** Simple manual dependency container. No DI framework, no reflection, no third-party SDKs. */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.get(context)
    val settings: SettingsRepository = SettingsRepository(context)
    val transactions: TransactionRepository = TransactionRepository(db.transactionDao(), db.reviewDao())
    val budgets: BudgetRepository = BudgetRepository(db.budgetDao())
    val parser: SmsParser = SmsParser()
    val importer: SmsImporter = SmsImporter(context, parser, transactions, settings)
}

class FinanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FinanceApp).container
