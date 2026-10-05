package com.pft.financetracker.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.domain.backup.BackupCodec
import com.pft.financetracker.domain.backup.BackupException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Writes every table of the database into one passphrase-sealed file, and reads one back. Tables are found from the
 * database itself, so tables added in later versions are included without changing this code. A restore replaces
 * everything in one transaction: if anything goes wrong, nothing changes.
 */
class BackupService(private val db: AppDatabase, private val codec: BackupCodec = BackupCodec()) {

    fun tables(): List<String> {
        val out = mutableListOf<String>()
        db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name"
        ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun export(passphrase: CharArray): ByteArray {
        val sql = db.openHelper.readableDatabase
        val tables = JSONObject()
        for (t in tables()) {
            sql.query("SELECT * FROM `$t`").use { c ->
                val rows = JSONArray()
                while (c.moveToNext()) rows.put(JSONArray().apply { for (i in 0 until c.columnCount) put(value(c, i)) })
                tables.put(t, JSONObject().put("columns", JSONArray(c.columnNames.toList())).put("rows", rows))
            }
        }
        val doc = JSONObject().put("app", APP).put("schema", sql.version).put("createdAt", System.currentTimeMillis()).put("tables", tables)
        return codec.encrypt(doc.toString(), passphrase)
    }

    /** Replaces all data with the backup's. Throws [BackupException] (and changes nothing) when it cannot. */
    fun restore(file: ByteArray, passphrase: CharArray) {
        val doc = try { JSONObject(codec.decrypt(file, passphrase)) } catch (e: JSONException) { throw BackupException.NotABackup() }
        if (doc.optString("app") != APP) throw BackupException.NotABackup()
        val sql = db.openHelper.writableDatabase
        if (doc.optInt("schema", Int.MAX_VALUE) > sql.version) throw BackupException.TooNew()
        val data = doc.optJSONObject("tables") ?: throw BackupException.NotABackup()
        val current = tables()
        db.runInTransaction {
            // Rows go back in any order; foreign keys are checked once, at the end.
            sql.execSQL("PRAGMA defer_foreign_keys = ON")
            current.forEach { sql.execSQL("DELETE FROM `$it`") }
            for (t in current) {
                val table = data.optJSONObject(t) ?: continue
                val existing = columns(t)
                val cols = table.getJSONArray("columns").let { a -> (0 until a.length()).map { a.getString(it) } }
                val rows = table.getJSONArray("rows")
                for (r in 0 until rows.length()) {
                    val row = rows.getJSONArray(r)
                    val cv = ContentValues()
                    cols.forEachIndexed { i, col -> if (col in existing) put(cv, col, row.opt(i)) }
                    sql.insert(t, SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
        }
    }

    private fun columns(table: String): Set<String> {
        val out = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c -> while (c.moveToNext()) out += c.getString(c.getColumnIndexOrThrow("name")) }
        return out
    }

    private fun value(c: Cursor, i: Int): Any = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_BLOB -> android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP)
        else -> c.getString(i)
    }

    private fun put(cv: ContentValues, col: String, v: Any?) = when (v) {
        null, JSONObject.NULL -> cv.putNull(col)
        is Int -> cv.put(col, v.toLong())
        is Long -> cv.put(col, v)
        is Double -> cv.put(col, v)
        is Boolean -> cv.put(col, if (v) 1L else 0L)
        else -> cv.put(col, v.toString())
    }

    private companion object {
        const val APP = "FinTrack"
    }
}
