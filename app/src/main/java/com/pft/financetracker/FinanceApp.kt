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
    val tax: com.pft.financetracker.data.tax.TaxService = com.pft.financetracker.data.tax.TaxService(db.transactionDao(), db.taxDao())
    val backup: com.pft.financetracker.data.backup.BackupService = com.pft.financetracker.data.backup.BackupService(db)
    val nano: com.pft.financetracker.data.ai.NanoAi = com.pft.financetracker.data.ai.NanoAi()
    val importer: SmsImporter = SmsImporter(
        context, parser, transactions, smsLog, settings,
        onCardStatement = { s, bank -> bills.fromStatement(s, bank) },
    )
    val splitEngine: SplitEngine = SplitEngine(
        db.transactionDao(), db.splitDao(),
        ai = OpenAiSplitProvider({ settings.getApiKey() }),
        aiEnabled = { settings.splitAi.value && settings.hasApiKey.value },
        myName = { settings.myName.value },
        cache = settings.aiAnswerCache,
    )
    val statementFiles: StatementFiles = StatementFiles(context)
    val statementImporter: StatementImporter = StatementImporter(db.transactionDao(), transactions, db.importDao(), smsLog)
    /** Everything that can have a due date. Features add themselves here; [com.pft.financetracker.data.reminders.ReminderWorker] reads it. */
    val reminderSources: MutableList<ReminderSource> = mutableListOf()
    val refunds: RefundLinker = RefundLinker(db.transactionDao(), db.refundDao())
    val recurring: com.pft.financetracker.data.recurring.RecurringService = com.pft.financetracker.data.recurring.RecurringService(db.transactionDao(), db.recurringDao())

    /** Every change a person makes to payments goes through here; it also runs the follow-up below, one at a time. */
    val ledger: com.pft.financetracker.data.ledger.Ledger = com.pft.financetracker.data.ledger.Ledger(
        transactions, smsLog, refunds, importer, statementImporter,
        afterChange = { useAi -> afterChange(useAi) },
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
    )

    /**
     * Everything that derives from the transactions, re-run after any import, edit or delete: refund pairing first
     * (it never touches money from people), then split intelligence. Each step is idempotent and isolated, so one
     * failing never blocks the others. Asked for through [ledger], which never runs two at once.
     */
    private suspend fun afterChange(useAi: Boolean) {
        runCatching { refunds.run() }
        runCatching { splitEngine.run(useAi) }
        com.pft.financetracker.ui.widget.FinTrackWidget.refresh(appContext)
    }
}

class FinanceApp : Application() {
    /** Null only when the database's key could not be read; [startupError] says why, and MainActivity explains. */
    var container: AppContainer? = null
        private set
    var startupError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
        start()
    }

    /** Opens the data. False when the secure key store can't give the key; the data is left exactly as it was. */
    fun start(): Boolean {
        val c = try {
            AppContainer(this)
        } catch (e: com.pft.financetracker.data.local.DbKeyUnavailable) {
            startupError = e
            return false
        }
        startupError = null
        container = c
        onStarted(c)
        return true
    }

    /**
     * The person chose to start again after the key store broke: the unreadable database is moved aside (kept on the
     * phone, never deleted) and a new key and an empty database are made. A backup can then be restored in Settings.
     */
    fun startFresh(): Boolean {
        val db = getDatabasePath(com.pft.financetracker.data.local.DbKey.DB_NAME)
        val kept = com.pft.financetracker.data.local.KeyRecovery.keptName(System.currentTimeMillis())
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val f = java.io.File(db.path + suffix)
            if (f.exists() && !f.renameTo(java.io.File(db.parentFile, kept + suffix))) return false
        }
        com.pft.financetracker.data.local.DbKey.reset(this)
        return start()
    }

    private fun onStarted(container: AppContainer) {
        seedBuiltInApiKey()
        container.reminderSources += ReminderSource { now -> container.recurring.book(now).reminders() }
        container.reminderSources += ReminderSource { now -> container.bills.reminders(now) }
        // Only for people who already keep backups: a nudge when the last one is a month old, then at most weekly.
        container.reminderSources += ReminderSource { now ->
            listOfNotNull(com.pft.financetracker.domain.reminders.BackupNudge.reminder(container.settings.lastBackupAt.value, now))
        }
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
        container?.settings?.seedApiKey(seed)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as FinanceApp).container ?: error("FinTrack could not open its data.")

/** The container, or null when the data could not be opened (background work then simply skips). */
val Context.appContainerOrNull: AppContainer?
    get() = (applicationContext as FinanceApp).container
