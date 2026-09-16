package com.pft.financetracker.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

@Database(
    entities = [TransactionEntity::class, ReviewItemEntity::class, BudgetEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun budgetDao(): BudgetDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
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
