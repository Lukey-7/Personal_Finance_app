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
        seedBuiltInApiKey()
    }

    /**
     * A build can carry a built-in OpenAI key (OPENAI_API_KEY at build time: every debug build, and a
     * release only when FINTRACK_EMBED_KEY=1 asks for a personal build). It is copied into encrypted
     * storage when no key is saved yet, or when a previous build put the current one there, so rebuilding
     * with a new key takes effect. Once you change or remove the key in Settings it is yours and is never
     * overwritten. Ordinary release builds carry no key, and the key is never logged.
     */
    private fun seedBuiltInApiKey() {
        val seed = BuildConfig.SEED_OPENAI_KEY
        if (seed.isBlank()) return
        container.settings.seedApiKey(seed)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FinanceApp).container
