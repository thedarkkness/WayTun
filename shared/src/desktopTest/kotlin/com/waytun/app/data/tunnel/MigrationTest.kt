package com.waytun.app.data.tunnel

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Opens databases written by older app versions with today's Room KMP setup: the migrations must bring them to the
 * current schema (Room validates every table against the entities afterwards) without losing a row.
 */
class MigrationTest {

    private val dir = createTempDirectory("waytun-db").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Version 1 (the first release): only the `tunnels` table, exactly as Room created it then. */
    private fun createVersion1(file: File, upTo: Int = 1) {
        BundledSQLiteDriver().open(file.absolutePath).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tunnels` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `protocol` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `encryptedConfig` BLOB NOT NULL, " +
                    "`configIv` BLOB NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "INSERT INTO tunnels (id, name, protocol, sortOrder, createdAtEpochMillis, encryptedConfig, configIv) " +
                    "VALUES ('t1', '🇫🇮 Финляндия #1', 'AMNEZIA_WG', 0, 1700000000000, X'010203', X'0A0B')"
            )
            DatabaseMigrations.steps.filter { it.to <= upTo }.forEach { step -> step.statements.forEach(db::execSQL) }
            db.execSQL("PRAGMA user_version = $upTo")
        }
    }

    @Test
    fun firstReleaseDatabaseMigratesToCurrent() = runBlocking {
        val file = File(dir, AppDatabase.FILE_NAME)
        createVersion1(file)
        val db = openDesktopDatabase(file)
        try {
            val tunnel = db.tunnelDao().getById("t1")!!
            assertEquals("🇫🇮 Финляндия #1", tunnel.name)
            assertEquals("AMNEZIA_WG", tunnel.protocol)
            assertContentEquals(byteArrayOf(1, 2, 3), tunnel.encryptedConfig)
            assertContentEquals(byteArrayOf(10, 11), tunnel.configIv)
            assertNull(tunnel.subscriptionId)
            assertNull(tunnel.details)
            assertEquals(1, db.tunnelDao().observeAll().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun version6DatabaseKeepsSubscriptionsAndGainsBotLink() = runBlocking {
        val file = File(dir, AppDatabase.FILE_NAME)
        createVersion1(file, upTo = 6)
        BundledSQLiteDriver().open(file.absolutePath).use { db ->
            db.execSQL(
                "INSERT INTO subscriptions (id, name, encryptedUrl, urlIv, createdAtEpochMillis, updatedAtEpochMillis, uploadBytes, " +
                    "downloadBytes, totalBytes, expireEpochSeconds, updateIntervalHours, announce, supportUrl, webPageUrl, collapsed, " +
                    "description, announceUrl, supportEmail, autoUpdate, userIntervalHours, updateOnOpen, pingOnOpen, sendHwid, " +
                    "timeoutSeconds, userAgent, sortMode, pinned) VALUES ('s1', 'DarkVPN', X'AA', X'BB', 1, 2, 0, 2040109465, " +
                    "37580963840, 1799000000, 1, 'note', 'https://t.me/support', NULL, 0, NULL, NULL, NULL, 1, NULL, 0, 1, 1, 15, NULL, 2, 1)"
            )
            db.execSQL("UPDATE tunnels SET subscriptionId = 's1', details = 'VLESS / TCP / REALITY | JSON' WHERE id = 't1'")
        }
        val db = openDesktopDatabase(file)
        try {
            val sub = db.subscriptionDao().getById("s1")!!
            assertEquals("DarkVPN", sub.name)
            assertEquals(37580963840L, sub.totalBytes)
            assertEquals(1, sub.updateIntervalHours)
            assertEquals("https://t.me/support", sub.supportUrl)
            assertEquals(2, sub.sortMode)
            assertEquals(true, sub.pinned)
            assertNull(sub.botLink)
            assertEquals("VLESS / TCP / REALITY | JSON", db.tunnelDao().getById("t1")!!.details)
            assertEquals(listOf("t1"), db.tunnelDao().getBySubscription("s1").map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun transactionRollsBackEveryDaoCallOnFailure() = runBlocking {
        val db = openDesktopDatabase(File(dir, AppDatabase.FILE_NAME))
        try {
            val entity = TunnelEntity("t9", "x", "VLESS", 0, 0, byteArrayOf(1), byteArrayOf(2))
            runCatching {
                db.inTransaction {
                    db.tunnelDao().insert(entity)
                    error("boom")
                }
            }
            assertNull(db.tunnelDao().getById("t9"))
            db.inTransaction { db.tunnelDao().insert(entity) }
            assertEquals("x", db.tunnelDao().getById("t9")!!.name)
        } finally {
            db.close()
        }
    }
}
