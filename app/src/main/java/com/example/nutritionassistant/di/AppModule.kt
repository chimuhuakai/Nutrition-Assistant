package com.example.nutritionassistant.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.nutritionassistant.data.local.ApiKeyManager
import com.example.nutritionassistant.data.local.Database
import com.example.nutritionassistant.data.local.ReminderPreferences
import com.example.nutritionassistant.data.remote.api.AiApiService
import com.example.nutritionassistant.data.remote.api.TianApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): Database {
        return Room.databaseBuilder(
            context,
            Database::class.java,
            "nutrition_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    // 提供各个 DAO
    @Provides
    fun provideFoodRecordDao(db: Database) = db.foodRecordDao()

    @Provides
    fun provideUserProfileDao(db: Database) = db.userProfileDao()

    @Provides
    fun provideBarcodeCacheDao(db: Database) = db.barcodeCacheDao()

    // 提供 OkHttpClient (自动添加 API Key)
    // 天聚数行专用 OkHttpClient
    @Provides
    @Singleton
    @Named("tianapi")
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val key = ApiKeyManager.tianApiKey
                if (key.isNotEmpty()) {
                    val url = chain.request().url.newBuilder()
                        .addQueryParameter("key", key)
                        .build()
                    chain.proceed(chain.request().newBuilder().url(url).build())
                } else {
                    // 没有 Key，直接返回错误响应，避免请求浪费
                    throw java.io.IOException("")
                }
            }
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()
    }

    @Provides
    @Singleton
    fun provideTianApiService(@Named("tianapi") okHttpClient: OkHttpClient): TianApiService {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())   //  ★ 加上这个
            .build()

        return Retrofit.Builder()
            .baseUrl("https://apis.tianapi.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TianApiService::class.java)
    }

    /**
     * 提供 AI 接口服务（通义千问 / 大模型 API）
     * 专门给 AI 接口配置独立的 OkHttpClient，自动携带 Token
     * 作用：创建 AiApiService 实例，供 AiRepository 使用
     */
    @Provides @Singleton
    fun provideAiApiService(@Named("ai") okHttpClient: OkHttpClient): AiApiService {
        return Retrofit.Builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/")  // 固定千问 URL
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }

    // AI OkHttpClient
    @Provides @Singleton @Named("ai")
    fun provideAiOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val key = ApiKeyManager.aiApiKey
                if (key.isNotEmpty()) {
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $key")
                        .build()
                    chain.proceed(request)
                } else {
                    throw java.io.IOException("AI API Key 未设置")
                }
            }
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()
    }

    @Provides
    @Singleton
    fun provideReminderPreferences(@ApplicationContext context: Context): ReminderPreferences {
        return ReminderPreferences(context)
    }
}