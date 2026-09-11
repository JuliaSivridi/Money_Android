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
        Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db")
            .fallbackToDestructiveMigration(dropAllTables = true)  // safety net; no legacy schema to preserve yet
            .build()

    @Provides fun provideTransactionDao(db: MoneyDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideAccountDao(db: MoneyDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: MoneyDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideSyncQueueDao(db: MoneyDatabase): SyncQueueDao = db.syncQueueDao()
}
