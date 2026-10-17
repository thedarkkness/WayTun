package com.waytun.app.data.tunnel

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.Dispatchers

fun DatabaseMigrations.driver(): Array<Migration> = steps.map { step ->
    object : Migration(step.from, step.to) {
        override fun migrate(connection: SQLiteConnection) {
            step.statements.forEach(connection::execSQL)
        }
    }
}.toTypedArray()

/** The desktop database: bundled SQLite (same file format as Android), same schema and migrations. */
fun openDesktopDatabase(file: File): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*DatabaseMigrations.driver())
        .build()

actual suspend fun <R> AppDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
