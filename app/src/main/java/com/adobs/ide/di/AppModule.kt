package com.adobs.ide.di

import com.adobs.ide.core.monetization.AdManagerServiceImpl
import com.adobs.ide.core.monetization.IAdManager
import com.adobs.ide.core.storage.FileEngineImpl
import com.adobs.ide.core.storage.IFileEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module that binds interface contracts to their concrete implementations.
 *
 * Without this module Hilt cannot satisfy the @Inject dependencies in
 * ExplorerViewModel (IFileEngine) and ExplorerActivity (IAdManager), which
 * causes the app to crash with an UnsatisfiedDependencyException on launch.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * Binds [FileEngineImpl] as the singleton provider of [IFileEngine].
     * Used by [ExplorerViewModel] to perform all file-system operations.
     */
    @Binds
    @Singleton
    abstract fun bindFileEngine(impl: FileEngineImpl): IFileEngine

    /**
     * Binds [AdManagerServiceImpl] as the singleton provider of [IAdManager].
     * Used by [ExplorerActivity] to init, load, and show AdMob ads.
     */
    @Binds
    @Singleton
    abstract fun bindAdManager(impl: AdManagerServiceImpl): IAdManager
}
