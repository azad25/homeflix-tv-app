package com.homeflix.tv.di

import com.homeflix.tv.data.repository.MediaRepository
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
    abstract fun bindMediaRepository(
        mediaRepository: MediaRepository
    ): com.homeflix.tv.domain.repository.MediaRepository
}