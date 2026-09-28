package com.pft.financetracker.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

@Database(
    entities = [
        TransactionEntity::class, ReviewItemEntity::class, BudgetEntity::class,
        SmsLogEntity::class, SplitEntity::class, SplitPersonEntity::class, SplitShareEntity::class, SplitItemEntity::class, RecentPersonEntity::class,
        SplitLinkEntity::class, SplitDecisionEntity::class, ImportBatchEntity::class, ImportMatchEntity::class,
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun budgetDao(): BudgetDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun splitDao(): SplitDao
    abstract fun importDao(): ImportDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * The database file is encrypted with SQLCipher (AES-256). The 256-bit passphrase is random per install and
         * stored in EncryptedSharedPreferences, whose master key lives in the Android Keystore. It is never logged,
         * exported, or sent anywhere.
         */
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(DbKey.getOrCreate(context.applicationContext))
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fintrack.db")
                    .openHelperFactory(factory)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            // Overwrite deleted rows with zeros so "Clear all data" leaves nothing recoverable.
                            db.query("PRAGMA secure_delete = ON").close()
                        }
                    })
                    .addMigrations(*ALL_MIGRATIONS)
                    // No destructive fallback: a missing migration must fail loudly in tests, never wipe user data.
                    .build()
                    .also { instance = it }
            }
        }

        /**
         * v1 -> v2: money moves from REAL rupees to INTEGER paise, transactions gain flow + refNumber,
         * and the sms_log / split tables are created. Existing rows are preserved.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // transactions: SQLite cannot change a column type, so rebuild the table.
                db.execSQL(
                    """CREATE TABLE transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        merchant TEXT NOT NULL,
                        category TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        bankName TEXT,
                        accountRef TEXT,
                        source TEXT NOT NULL,
                        flow TEXT NOT NULL,
                        note TEXT,
                        smsHash TEXT,
                        refNumber TEXT,
                        confidence INTEGER NOT NULL,
                        needsReview INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
                // Flow for old rows: debits default to EXPENSE except ATM/INVESTMENT/TRANSFER categories; credits
                // default to INCOME. Users can refine per transaction; new imports are classified by FlowClassifier.
                db.execSQL(
                    """INSERT INTO transactions_new (id, amountPaise, type, merchant, category, timestamp, bankName, accountRef, source, flow, note, smsHash, refNumber, confidence, needsReview, createdAt)
                       SELECT id, CAST(ROUND(amount * 100) AS INTEGER), type, merchant, category, timestamp, bankName, accountRef, source,
                              CASE
                                WHEN type = 'CREDIT' THEN 'INCOME'
                                WHEN category = 'ATM' THEN 'CASH'
                                WHEN category = 'INVESTMENT' THEN 'INVESTMENT'
                                WHEN category = 'TRANSFER' THEN 'TRANSFER'
                                ELSE 'EXPENSE'
                              END,
                              note, smsHash, NULL, confidence, needsReview, createdAt
                       FROM transactions"""
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_smsHash ON transactions (smsHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_refNumber ON transactions (refNumber)")

                // review_queue: guessedAmount REAL -> guessedAmountPaise INTEGER.
                db.execSQL(
                    """CREATE TABLE review_queue_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sender TEXT NOT NULL, body TEXT NOT NULL, receivedAt INTEGER NOT NULL, smsHash TEXT NOT NULL,
                        guessedAmountPaise INTEGER, guessedType TEXT, reason TEXT NOT NULL
                    )"""
                )
                db.execSQL(
                    """INSERT INTO review_queue_new (id, sender, body, receivedAt, smsHash, guessedAmountPaise, guessedType, reason)
                       SELECT id, sender, body, receivedAt, smsHash, CASE WHEN guessedAmount IS NULL THEN NULL ELSE CAST(ROUND(guessedAmount * 100) AS INTEGER) END, guessedType, reason FROM review_queue"""
                )
                db.execSQL("DROP TABLE review_queue")
                db.execSQL("ALTER TABLE review_queue_new RENAME TO review_queue")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_review_queue_smsHash ON review_queue (smsHash)")

                // budgets: monthlyLimit REAL -> monthlyLimitPaise INTEGER.
                db.execSQL("CREATE TABLE budgets_new (category TEXT PRIMARY KEY NOT NULL, monthlyLimitPaise INTEGER NOT NULL)")
                db.execSQL("INSERT INTO budgets_new (category, monthlyLimitPaise) SELECT category, CAST(ROUND(monthlyLimit * 100) AS INTEGER) FROM budgets")
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")

                // New tables.
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sms_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sender TEXT NOT NULL, receivedAt INTEGER NOT NULL, outcome TEXT NOT NULL, reason TEXT NOT NULL,
                        amountPaise INTEGER, type TEXT, transactionId INTEGER, smsHash TEXT NOT NULL, runId INTEGER NOT NULL, loggedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sms_log_smsHash ON sms_log (smsHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_log_receivedAt ON sms_log (receivedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_log_outcome ON sms_log (outcome)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS splits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL, totalPaise INTEGER NOT NULL, date INTEGER NOT NULL, mode TEXT NOT NULL,
                        payerIndex INTEGER NOT NULL, linkedTransactionId INTEGER, note TEXT, createdAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_splits_date ON splits (date)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS split_people (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, splitId INTEGER NOT NULL, personIndex INTEGER NOT NULL,
                        name TEXT NOT NULL, isMe INTEGER NOT NULL,
                        FOREIGN KEY(splitId) REFERENCES splits(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_people_splitId ON split_people (splitId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS split_shares (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, splitId INTEGER NOT NULL, personIndex INTEGER NOT NULL,
                        amountPaise INTEGER NOT NULL, settledPaise INTEGER NOT NULL,
                        FOREIGN KEY(splitId) REFERENCES splits(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_shares_splitId ON split_shares (splitId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS split_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, splitId INTEGER NOT NULL, name TEXT NOT NULL,
                        quantity INTEGER NOT NULL, pricePaise INTEGER NOT NULL, assignedTo TEXT NOT NULL,
                        FOREIGN KEY(splitId) REFERENCES splits(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_items_splitId ON split_items (splitId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recent_people (name TEXT PRIMARY KEY NOT NULL, lastUsedAt INTEGER NOT NULL)")
            }
        }

        /** v2 -> v3: transactions remember the bank's amount before a split, and whether a person edited them. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN originalAmountPaise INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3 -> v4: no schema change. v1.1.0's split flow shrank the linked SMS row to "my share" without recording
         * the bank amount, so a rescan saw a disagreement and put the full amount back. Record the bank amount on
         * those rows and restore my share where a rescan already reset it. A row whose amount is neither the total
         * nor my share was changed by a person and is left alone.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val fixes = mutableListOf<Triple<Long, Long, Long>>() // id, my share, bank total
                db.query(
                    """SELECT t.id, sh.amountPaise, s.totalPaise FROM transactions t
                       JOIN splits s ON s.linkedTransactionId = t.id
                       JOIN split_people p ON p.splitId = s.id AND p.isMe = 1
                       JOIN split_shares sh ON sh.splitId = s.id AND sh.personIndex = p.personIndex
                       WHERE t.source = 'SMS' AND t.originalAmountPaise IS NULL
                         AND sh.amountPaise < s.totalPaise AND t.amountPaise IN (s.totalPaise, sh.amountPaise)
                       ORDER BY s.id"""
                ).use { c -> while (c.moveToNext()) fixes += Triple(c.getLong(0), c.getLong(1), c.getLong(2)) }
                for ((id, mine, total) in fixes.distinctBy { it.first }) {
                    db.execSQL("UPDATE transactions SET amountPaise = ?, originalAmountPaise = ? WHERE id = ?", arrayOf<Any>(mine, total, id))
                }
            }
        }

        /**
         * v4 -> v5 (v1.2): statement import and split intelligence. Transactions learn who the other side is and
         * which import added them; splits learn whether they were found automatically; three new tables hold the
         * split links, "not a split" decisions and the import history. Existing rows are untouched.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN counterpartyKind TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN importBatchId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_importBatchId ON transactions (importBatchId)")
                db.execSQL("ALTER TABLE splits ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE splits ADD COLUMN status TEXT NOT NULL DEFAULT 'APPLIED'")
                db.execSQL("ALTER TABLE splits ADD COLUMN confidence INTEGER")
                db.execSQL("ALTER TABLE splits ADD COLUMN reasons TEXT")
                db.execSQL("ALTER TABLE splits ADD COLUMN kind TEXT NOT NULL DEFAULT 'PAYBACK'")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS split_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, splitId INTEGER NOT NULL, transactionId INTEGER NOT NULL,
                        role TEXT NOT NULL, allocatedPaise INTEGER NOT NULL, prevFlow TEXT, prevAmountPaise INTEGER,
                        FOREIGN KEY(splitId) REFERENCES splits(id) ON UPDATE NO ACTION ON DELETE CASCADE)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_links_splitId ON split_links (splitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_links_transactionId ON split_links (transactionId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS split_decisions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, paymentTransactionId INTEGER NOT NULL,
                        decision TEXT NOT NULL, decidedAt INTEGER NOT NULL)"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_split_decisions_paymentTransactionId ON split_decisions (paymentTransactionId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS import_batches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fileName TEXT NOT NULL, format TEXT NOT NULL,
                        importedAt INTEGER NOT NULL, rowsFound INTEGER NOT NULL, added INTEGER NOT NULL, duplicates INTEGER NOT NULL,
                        needsReview INTEGER NOT NULL, balanceMismatches INTEGER NOT NULL, firstDate INTEGER, lastDate INTEGER)"""
                )
            }
        }

        /**
         * v5 -> v6 (v1.2.1): which imports also contained a row, so undoing one import keeps rows another still has; and
         * which manual-split share a friend's transfer settled, so deleting the transfer makes the share owed again.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS import_matches (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, batchId INTEGER NOT NULL, transactionId INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_matches_batchId ON import_matches (batchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_matches_transactionId ON import_matches (transactionId)")
                db.execSQL("ALTER TABLE split_links ADD COLUMN shareId INTEGER")
                // Links made by v1.2.0's "settle with a transaction": find the share by the friend's name, where only one fits.
                val found = mutableListOf<Pair<Long, Long>>()
                db.query(
                    """SELECT l.id, l.splitId, t.merchant FROM split_links l JOIN splits s ON s.id = l.splitId JOIN transactions t ON t.id = l.transactionId
                       WHERE s.source = 'MANUAL' AND l.role != 'PAYMENT'"""
                ).use { c ->
                    while (c.moveToNext()) {
                        val linkId = c.getLong(0); val splitId = c.getLong(1); val merchant = c.getString(2)
                        val people = mutableListOf<Pair<Int, String>>()
                        db.query("SELECT personIndex, name FROM split_people WHERE splitId = ? AND isMe = 0", arrayOf<Any>(splitId)).use { p ->
                            while (p.moveToNext()) people += p.getInt(0) to p.getString(1)
                        }
                        val who = people.filter { (_, name) -> com.pft.financetracker.domain.split.PayerClassifier.sameParty(merchant, name) }.singleOrNull() ?: continue
                        db.query("SELECT id FROM split_shares WHERE splitId = ? AND personIndex = ?", arrayOf<Any>(splitId, who.first)).use { sh ->
                            if (sh.moveToNext()) found += linkId to sh.getLong(0)
                        }
                    }
                }
                for ((linkId, shareId) in found) db.execSQL("UPDATE split_links SET shareId = ? WHERE id = ?", arrayOf<Any>(shareId, linkId))
            }
        }

        val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}

/** Holds the SQLCipher passphrase. Kept in its own encrypted file so clearing settings never orphans the database. */
private object DbKey {
    private const val FILE = "fintrack_db_key"
    private const val KEY = "db_passphrase"

    fun getOrCreate(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            context, FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val existing = prefs.getString(KEY, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // commit() (not apply) so the key is on disk before the database is created with it.
        prefs.edit().putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
        return bytes.copyOf()
    }
}
