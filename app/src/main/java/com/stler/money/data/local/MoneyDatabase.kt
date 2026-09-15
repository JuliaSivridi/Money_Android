package com.stler.money.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.stler.money.data.local.dao.AccountDao
import com.stler.money.data.local.dao.AccountNotificationSourceDao
import com.stler.money.data.local.dao.CategoryDao
import com.stler.money.data.local.dao.SyncQueueDao
import com.stler.money.data.local.dao.TransactionDao
import com.stler.money.data.local.dao.TransactionDraftDao
import com.stler.money.data.local.entity.AccountEntity
import com.stler.money.data.local.entity.AccountNotificationSourceEntity
import com.stler.money.data.local.entity.CategoryEntity
import com.stler.money.data.local.entity.SyncQueueEntity
import com.stler.money.data.local.entity.TransactionCategoryCrossRef
import com.stler.money.data.local.entity.TransactionDraftEntity
import com.stler.money.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionCategoryCrossRef::class,
        SyncQueueEntity::class,
        TransactionDraftEntity::class,
        AccountNotificationSourceEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun transactionDraftDao(): TransactionDraftDao
    abstract fun accountNotificationSourceDao(): AccountNotificationSourceDao
}
