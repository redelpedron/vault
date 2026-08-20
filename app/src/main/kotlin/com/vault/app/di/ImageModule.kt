package com.vault.app.di

import android.content.Context
import coil3.ImageLoader
import com.vault.app.presentation.browser.ThumbnailFetcher
import com.vault.app.presentation.browser.ThumbnailKeyer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One app-wide ImageLoader, provided explicitly through Hilt rather than
 * Coil's global-singleton setter — consistent with this app's existing
 * pattern (NetworkModule, RepositoryModule) of explicit DI over framework
 * magic. Screens read it via FileBrowserViewModel and pass it to
 * AsyncImage's imageLoader parameter directly; see ThumbnailFetcher.kt for
 * why a custom Fetcher/Keyer is needed at all instead of Coil's defaults.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        thumbnailFetcherFactory: ThumbnailFetcher.Factory,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(thumbnailFetcherFactory)
            add(ThumbnailKeyer())
        }
        .build()
}
