package com.waytun.app.data.tunnel

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waytun.app.data.subscription.SubscriptionDao
import com.waytun.app.data.subscription.SubscriptionEntity

@Database(entities = [TunnelEntity::class, SubscriptionEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tunnelDao(): TunnelDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `autoUpdate` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `userIntervalHours` INTEGER")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `updateOnOpen` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `pingOnOpen` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `sendHwid` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `timeoutSeconds` INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `userAgent` TEXT")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `sortMode` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `description` TEXT")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `announceUrl` TEXT")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `supportEmail` TEXT")
                // Re-derive the short stack labels ("VLESS / TCP / REALITY | JSON") for existing tunnels.
                db.execSQL("UPDATE `tunnels` SET `details` = NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tunnels` ADD COLUMN `details` TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `announce` TEXT")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `supportUrl` TEXT")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `webPageUrl` TEXT")
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `collapsed` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tunnels` ADD COLUMN `subscriptionId` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subscriptions` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `encryptedUrl` BLOB NOT NULL, " +
                        "`urlIv` BLOB NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                        "`updatedAtEpochMillis` INTEGER NOT NULL, `uploadBytes` INTEGER, " +
                        "`downloadBytes` INTEGER, `totalBytes` INTEGER, `expireEpochSeconds` INTEGER, " +
                        "`updateIntervalHours` INTEGER, PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
