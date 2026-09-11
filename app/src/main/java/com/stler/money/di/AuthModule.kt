package com.stler.money.di

import com.stler.money.auth.GoogleAuthRepository
import com.stler.money.data.remote.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    /**
     * Binds GoogleAuthRepository as the TokenProvider implementation.
     * The repository reads tokens from DataStore and refreshes via
     * Identity.getAuthorizationClient() when they are about to expire.
     */
    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: GoogleAuthRepository): TokenProvider
}
