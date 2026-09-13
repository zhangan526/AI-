package com.geekathon.guardpet

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

enum class FlashNoteCategory(val key: String, val labelRes: Int) {
    SCHEDULE("schedule", R.string.category_schedule),
    IDEA("idea", R.string.category_idea),
    DIARY("diary", R.string.category_diary),
    TODO("todo", R.string.category_todo),
    OTHER("other", R.string.category_other);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

data class FlashNote(
    val id: Long = 0,
    val text: String,
    val category: FlashNoteCategory,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduleDate: String? = null
)

object FlashNoteStore {
    private lateinit var helper: Helper

    fun init(context: Context) {
        helper = Helper(context.applicationContext)
    }

    fun insert(note: FlashNote): Long {
        val values = ContentValues().apply {
            put("text", note.text)
            put("category", note.category.key)
            put("source", note.source)
            put("created_at", note.createdAt)
            put("schedule_date", note.scheduleDate)
        }
        return helper.writableDatabase.insert("flash_notes", null, values)
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("flash_notes", "id=?", arrayOf(id.toString()))
    }

    fun all(): List<FlashNote> = query(null, null)

    fun todaySchedules(today: LocalDate = LocalDate.now()): List<FlashNote> =
        query(
            "category=? AND schedule_date=?",
            arrayOf(FlashNoteCategory.SCHEDULE.key, today.toString())
        )

    private fun query(selection: String?, args: Array<String>?): List<FlashNote> {
        val cursor = helper.readableDatabase.query(
            "flash_notes",
            null,
            selection,
            args,
            null,
            null,
            "created_at DESC"
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        FlashNote(
                            id = it.getLong(it.getColumnIndexOrThrow("id")),
                            text = it.getString(it.getColumnIndexOrThrow("text")),
                            category = FlashNoteCategory.fromKey(
                                it.getString(it.getColumnIndexOrThrow("category"))
                            ),
                            source = it.getString(it.getColumnIndexOrThrow("source")),
                            createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                            scheduleDate = it.getString(it.getColumnIndexOrThrow("schedule_date"))
                        )
                    )
                }
            }
        }
    }

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, "flash_notes.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE flash_notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT NOT NULL,
                    category TEXT NOT NULL,
                    source TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    schedule_date TEXT
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
