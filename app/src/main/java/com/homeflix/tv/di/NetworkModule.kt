package com.homeflix.tv.di

import android.content.Context
import com.homeflix.tv.BuildConfig
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.util.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor {
        return NetworkMonitor(context)
    }
    
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            // LOCAL-first, domain-fallback server selection (must run first)
            .addInterceptor(com.homeflix.tv.util.ServerFailoverInterceptor())
            // The backend keys playback progress / my-list / recently-watched
            // on the X-User-ID header (defaults to "anonymous" when absent).
            // The web app writes as user "1"; send the same identity on EVERY
            // request so TV and web share one watch history. Only two POST
            // endpoints set it explicitly before — GETs silently read the
            // empty "anonymous" profile, which is why Resume never appeared.
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("X-User-ID") == null) {
                    chain.proceed(request.newBuilder().header("X-User-ID", "1").build())
                } else {
                    chain.proceed(request)
                }
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL) // Use BASE_URL from build.gradle
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideHomeFlixApiService(retrofit: Retrofit): HomeFlixApiService {
        return retrofit.create(HomeFlixApiService::class.java)
    }
}