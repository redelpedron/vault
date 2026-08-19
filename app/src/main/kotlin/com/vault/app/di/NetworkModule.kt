package com.vault.app.di

import com.vault.app.data.remote.AuthInterceptor
import com.vault.app.data.remote.DynamicBaseUrlInterceptor
import com.vault.app.data.remote.OrgApi
import com.vault.app.data.remote.VaultApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // forward-compatible with server fields this app doesn't know about yet
        isLenient = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BODY-level logging would print vault file bytes and the raw
            // X-Vault-Token to logcat — HEADERS is already borderline
            // (it leaks the token to logcat on a debug build) but is kept
            // for now since this is a debug-only build; drop to NONE
            // before ever shipping a release variant.
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Placeholder — every real request's host/port is rewritten by
            // DynamicBaseUrlInterceptor before it leaves the device. This
            // must still be a syntactically valid absolute URL ending in
            // '/', or Retrofit.Builder.build() throws at construction time.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideVaultApi(retrofit: Retrofit): VaultApi = retrofit.create(VaultApi::class.java)

    @Provides
    @Singleton
    fun provideOrgApi(retrofit: Retrofit): OrgApi = retrofit.create(OrgApi::class.java)
}
