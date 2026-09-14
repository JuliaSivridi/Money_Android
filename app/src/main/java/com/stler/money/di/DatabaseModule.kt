package com.stler.money.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stler.money.data.local.MoneyDatabase
import com.stler.money.data.local.dao.AccountDao
import com.stler.money.data.local.dao.CategoryDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MoneyDatabase =
        // No fallbackToDestructiveMigration: at version 1 it was a no-op anyway (nothing to
        // migrate from), but leaving it in was a footgun for the *next* schema bump — it
        // would silently wipe money.db, sync_queue included, instead of forcing a real
        // migration to be written. Room now crashes loudly on an unmigrated version bump,
        // which is the outcome you actually want here.
        Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideTransactionDao(db: MoneyDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideAccountDao(db: MoneyDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: MoneyDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideSyncQueueDao(db: MoneyDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideTransactionDraftDao(db: MoneyDatabase): TransactionDraftDao = db.transactionDraftDao()
}
