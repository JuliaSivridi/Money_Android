package com.stler.money.di

import android.content.Context
import androidx.room.Room
import com.stler.money.data.local.MoneyDatabase
import com.stler.money.data.local.dao.AccountDao
import com.stler.money.data.local.dao.CategoryDao
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MoneyDatabase =
        // No fallbackToDestructiveMigration: at version 1 it was a no-op anyway (nothing to
        // migrate from), but leaving it in was a footgun for the *next* schema bump — it
        // would silently wipe money.db, sync_queue included, instead of forcing a real
        // migration to be written. Room now crashes loudly on an unmigrated version bump,
        // which is the outcome you actually want here.
        Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db").build()

    @Provides fun provideTransactionDao(db: MoneyDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideAccountDao(db: MoneyDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: MoneyDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideSyncQueueDao(db: MoneyDatabase): SyncQueueDao = db.syncQueueDao()
}
