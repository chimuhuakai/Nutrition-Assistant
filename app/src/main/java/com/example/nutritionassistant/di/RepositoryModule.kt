package com.example.nutritionassistant.di

import com.example.nutritionassistant.data.local.dao.BarcodeCacheDao
import com.example.nutritionassistant.data.local.dao.FoodRecordDao
import com.example.nutritionassistant.data.local.dao.UserProfileDao
import com.example.nutritionassistant.data.remote.api.AiApiService
import com.example.nutritionassistant.data.repository.AiRepository
import com.example.nutritionassistant.data.repository.BarcodeRepository
import com.example.nutritionassistant.data.repository.FoodRepository
import com.example.nutritionassistant.data.repository.UserRepository
import com.example.nutritionassistant.domain.usecase.GenerateMealPlanUseCase
import com.google.android.datatransport.runtime.dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserRepository(dao: UserProfileDao): UserRepository {
        return UserRepository(dao)
    }

    @Provides
    @Singleton
    fun provideBarcodeRepository(dao: BarcodeCacheDao): BarcodeRepository {
        return BarcodeRepository(dao)
    }

    @Provides
    @Singleton
    fun provideFoodRepository(dao: FoodRecordDao): FoodRepository {
        return FoodRepository(dao)
    }

    // ======================== AI 相关依赖提供 ========================

    /**
     * 提供 AI 仓库实例
     * Hilt 会自动创建 AiRepository 并管理
     */
    @Provides
    @Singleton
    fun provideAiRepository(api: AiApiService): AiRepository {
        return AiRepository(api)
    }

    /**
     * 提供【生成个性化餐食推荐】用例
     * 把需要的三个仓库自动注入进去
     * 让 ViewModel 可以直接使用这个用例
     */
    @Provides
    @Singleton
    fun provideGenerateMealPlanUseCase(
        userRepo: UserRepository,    // 用户仓库
        foodRepo: FoodRepository,    // 饮食记
        // 录仓库
        aiRepo: AiRepository         // AI 对话仓库
    ): GenerateMealPlanUseCase {
        return GenerateMealPlanUseCase(userRepo, foodRepo, aiRepo)
    }
}
