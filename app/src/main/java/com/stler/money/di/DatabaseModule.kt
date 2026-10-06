package com.stler.money.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stler.money.data.local.MoneyDatabase
import com.stler.money.data.local.dao.AccountDao
import com.stler.money.data.local.dao.AccountNotificationSourceDao
import com.stler.money.data.local.dao.CategoryDao
import com.stler.money.data.local.dao.ProcessedNotificationDao
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.dao.TransactionDao
import com.stler.money.data.local.dao.TransactionDraftDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Adds transaction_drafts (notification-derived draft suggestions — see notifications/). */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transaction_drafts` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `sourcePackage` TEXT NOT NULL,
                    `sourceLabel` TEXT NOT NULL,
                    `notificationKey` TEXT NOT NULL,
                    `textHash` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `currency` TEXT NOT NULL,
                    `date` TEXT NOT NULL,
                    `createdAt` TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /** Adds transaction_drafts.accountId + account_notification_sources (per-account
     * notification-package recognition — see notifications/ and AccountFormSheet). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transaction_drafts ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `account_notification_sources` (
                    `accountId` TEXT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    PRIMARY KEY(`accountId`, `packageName`)
                )
                """.trimIndent(),
            )
        }
    }

    /** Adds processed_notifications (permanent per-notification dedup so rescans never
     * re-create a draft that was already confirmed/discarded — see notifications/). */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `processed_notifications` (
                    `sourcePackage` TEXT NOT NULL,
                    `notificationKey` TEXT NOT NULL,
                    `postedAt` INTEGER NOT NULL,
                    `processedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`sourcePackage`, `notificationKey`, `postedAt`)
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MoneyDatabase =
        // No fallbackToDestructiveMigration: at version 1 it was a no-op anyway (nothing to
        // migrate from), but leaving it in was a footgun for the *next* schema bump — it
        // would silently wipe money.db, sync_queue included, instead of forcing a real
        // migration to be written. Room now crashes loudly on an unmigrated version bump,
        // which is the outcome you actually want here.
        Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides fun provideTransactionDao(db: MoneyDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideAccountDao(db: MoneyDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: MoneyDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideSyncQueueDao(db: MoneyDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideTransactionDraftDao(db: MoneyDatabase): TransactionDraftDao = db.transactionDraftDao()
    @Provides fun provideAccountNotificationSourceDao(db: MoneyDatabase): AccountNotificationSourceDao = db.accountNotificationSourceDao()
    @Provides fun provideProcessedNotificationDao(db: MoneyDatabase): ProcessedNotificationDao = db.processedNotificationDao()
}
