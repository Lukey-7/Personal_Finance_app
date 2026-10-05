package com.pft.financetracker

import android.app.Application
import android.content.Context
import kotlinx.coroutines.launch
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.BudgetRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.SplitRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.importer.StatementFiles
import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.data.split.SplitEngine
import com.pft.financetracker.domain.ai.OpenAiSplitProvider
import com.pft.financetracker.domain.parser.SmsParser
import com.pft.financetracker.data.reminders.ReminderSource
import com.pft.financetracker.data.refunds.RefundLinker
import com.pft.financetracker.data.reminders.Reminders

/** Simple manual dependency container. No DI framework, no reflection, no third-party SDKs. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val db: AppDatabase = AppDatabase.get(context)
    val settings: SettingsRepository = SettingsRepository(context)
    val transactions: TransactionRepository = TransactionRepository(db.transactionDao(), db.reviewDao())
    val budgets: BudgetRepository = BudgetRepository(db.budgetDao())
    val smsLog: SmsLogRepository = SmsLogRepository(db.smsLogDao())
    val splits: SplitRepository = SplitRepository(db.splitDao())
    /** Message shapes the person taught by confirming Review items; the parser tries them first for their sender. */
    val templates: com.pft.financetracker.data.sms.TemplateStore = com.pft.financetracker.data.sms.TemplateStore(db.templateDao())
    val parser: SmsParser = SmsParser(templates = { templates.current })
    val bills: com.pft.financetracker.data.bills.BillService = com.pft.financetracker.data.bills.BillService(db.transactionDao(), db.billDao())
    val cards: com.pft.financetracker.data.cards.CardService = com.pft.financetracker.data.cards.CardService(db.transactionDao(), db.cardDao())
    val goals: com.pft.financetracker.data.goals.GoalService = com.pft.financetracker.data.goals.GoalService(db.goalDao())
    val importer: SmsImporter = SmsImporter(context, parser, transactions, smsLog, settings, onCardStatement = { s, bank -> bills.fromStatement(s, bank) })
    val splitEngine: SplitEngine = SplitEngine(
        db.transactionDao(), db.splitDao(),
        ai = OpenAiSplitProvider({ settings.getApiKey() }),
        aiEnabled = { settings.splitAi.value && settings.hasApiKey.value },
        myName = { settings.myName.value },
        cache = settings.aiAnswerCache,
    )
    val statementFiles: StatementFiles = StatementFiles(context)
    val statementImporter: StatementImporter = StatementImporter(db.transactionDao(), transactions, db.importDao())
    /** Everything that can have a due date. Features add themselves here; [com.pft.financetracker.data.reminders.ReminderWorker] reads it. */
    val reminderSources: MutableList<ReminderSource> = mutableListOf()
    val refunds: RefundLinker = RefundLinker(db.transactionDao(), db.refundDao())
    val recurring: com.pft.financetracker.data.recurring.RecurringService = com.pft.financetracker.data.recurring.RecurringService(db.transactionDao(), db.recurringDao())

    /**
     * Everything that derives from the transactions, re-run after any import, edit or delete: refund pairing first
     * (it never touches money from people), then split intelligence. Each step is idempotent and isolated, so one
     * failing never blocks the others.
     */
    suspend fun afterChange(useAi: Boolean = true) {
        runCatching { refunds.run() }
        runCatching { splitEngine.run(useAi) }
        com.pft.financetracker.ui.widget.FinTrackWidget.refresh(appContext)
    }
}

class FinanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        seedBuiltInApiKey()
        Reminders.ensureChannel(this)
        container.reminderSources += ReminderSource { now -> container.recurring.book(now).reminders() }
        container.reminderSources += ReminderSource { now -> container.bills.reminders(now) }
        publishShortcuts()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { container.templates.load() } }
        Reminders.schedule(this)
    }

    /**
     * A build can carry a built-in OpenAI key (OPENAI_API_KEY at build time: every debug build, and a
     * release only when FINTRACK_EMBED_KEY=1 asks for a personal build). It is copied into encrypted
     * storage when no key is saved yet, or when a previous build put the current one there, so rebuilding
     * with a new key takes effect. Once you change or remove the key in Settings it is yours and is never
     * overwritten. Ordinary release builds carry no key, and the key is never logged.
     */
    /** Long-press the app icon: note a purchase without opening the app. Published from code so debug builds' package names work. */
    private fun publishShortcuts() {
        fun shortcut(id: String, label: String, cash: Boolean) = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(label).setLongLabel(label)
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(com.pft.financetracker.ui.widget.QuickAddActivity.intent(this, cash).setAction(android.content.Intent.ACTION_VIEW))
            .build()
        runCatching {
            androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut("quick_add", "Add expense", false), shortcut("quick_cash", "Paid in cash", true)))
        }
    }

    private fun seedBuiltInApiKey() {
        val seed = BuildConfig.SEED_OPENAI_KEY
        if (seed.isBlank()) return
        container.settings.seedApiKey(seed)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FinanceApp).container
