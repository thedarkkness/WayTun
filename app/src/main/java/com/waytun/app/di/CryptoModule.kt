package com.waytun.app.di

import com.waytun.app.core.crypto.ConfigCipher
import com.waytun.app.core.crypto.KeystoreConfigCipher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    @Singleton
    abstract fun bindConfigCipher(impl: KeystoreConfigCipher): ConfigCipher
}
