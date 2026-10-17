package com.waytun.app.data.tunnel

import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room migrations over SupportSQLite: the framework SQLite and `waytun.db` exactly as 1.3.2 used them. */
fun DatabaseMigrations.android(): Array<Migration> = steps.map { step ->
    object : Migration(step.from, step.to) {
        override fun migrate(db: SupportSQLiteDatabase) {
            step.statements.forEach(db::execSQL)
        }
    }
}.toTypedArray()

actual suspend fun <R> AppDatabase.inTransaction(block: suspend () -> R): R = withTransaction(block)
