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
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun budgetDao(): BudgetDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun splitDao(): SplitDao

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

        val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
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
