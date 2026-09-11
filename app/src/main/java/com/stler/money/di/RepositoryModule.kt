package com.stler.money.di

import com.stler.money.data.repository.AccountRepository
import com.stler.money.data.repository.AccountRepositoryImpl
import com.stler.money.data.repository.CategoryRepository
import com.stler.money.data.repository.CategoryRepositoryImpl
import com.stler.money.data.repository.ExchangeRateRepository
import com.stler.money.data.repository.ExchangeRateRepositoryImpl
import com.stler.money.data.repository.TransactionRepository
import com.stler.money.data.repository.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindExchangeRateRepository(impl: ExchangeRateRepositoryImpl): ExchangeRateRepository
}
