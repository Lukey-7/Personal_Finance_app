package com.pft.financetracker

import android.app.Application
import android.content.Context
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.BudgetRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.SplitRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.parser.SmsParser

/** Simple manual dependency container. No DI framework, no reflection, no third-party SDKs. */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.get(context)
    val settings: SettingsRepository = SettingsRepository(context)
    val transactions: TransactionRepository = TransactionRepository(db.transactionDao(), db.reviewDao())
    val budgets: BudgetRepository = BudgetRepository(db.budgetDao())
    val smsLog: SmsLogRepository = SmsLogRepository(db.smsLogDao())
    val splits: SplitRepository = SplitRepository(db.splitDao())
    val parser: SmsParser = SmsParser()
    val importer: SmsImporter = SmsImporter(context, parser, transactions, smsLog, settings)
}

class FinanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        seedDebugApiKey()
    }

    /**
     * Debug builds can start with an OpenAI key taken from the OPENAI_API_KEY environment variable at
     * build time, so AI mode can be exercised without typing a key into the phone. It is only applied
     * when no key has been saved yet, so it never overwrites one you entered, and the field is always
     * empty in release builds. The key is never logged.
     */
    private fun seedDebugApiKey() {
        if (!BuildConfig.DEBUG) return
        val seed = BuildConfig.SEED_OPENAI_KEY
        if (seed.isBlank()) return
        if (container.settings.getApiKey().isNullOrBlank()) container.settings.setApiKey(seed)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FinanceApp).container
