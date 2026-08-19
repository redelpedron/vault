package com.vault.app.di

import com.vault.app.data.repository.OrgRepository
import com.vault.app.data.repository.OrgRepositoryImpl
import com.vault.app.data.repository.VaultRepository
import com.vault.app.data.repository.VaultRepositoryImpl
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
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    @Singleton
    abstract fun bindOrgRepository(impl: OrgRepositoryImpl): OrgRepository
}
