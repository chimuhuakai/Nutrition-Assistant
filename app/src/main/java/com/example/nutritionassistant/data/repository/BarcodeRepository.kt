package com.example.nutritionassistant.data.repository

import com.example.nutritionassistant.data.local.dao.BarcodeCacheDao
import com.example.nutritionassistant.data.local.entity.BarcodeCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 条形码 缓存仓库
 * 作用：本地缓存 扫码查询过的食品信息
 * 好处：同一个码扫第二次 → 不用联网，直接读本地，速度快、省流量
 */
// 单例：整个APP只创建一个实例
@Singleton
class BarcodeRepository @Inject constructor(
    // 注入DAO：本地数据库操作工具
    private val barcodeCacheDao: BarcodeCacheDao
) {

    /**
     * 根据 条形码 查询本地缓存
     * @return 有缓存返回实体，没缓存返回null
     */
    suspend fun getBarcodeCache(barcode: String): BarcodeCacheEntity? {
        return barcodeCacheDao.getBarcodeCache(barcode)
    }

    /**
     * 保存 食品信息 到本地缓存
     * 下次扫码直接用
     */
    suspend fun saveBarcodeCache(
        barcode: String,
        foodName: String,
        nutritionJson: String,
        calories: Float = 0f,
        protein: Float = 0f,
        fat: Float = 0f,
        carbs: Float = 0f
    ) {
        barcodeCacheDao.insertBarcodeCache(
            BarcodeCacheEntity(
                barcode = barcode,
                foodName = foodName,
                nutritionJson = nutritionJson,
                calories = calories,
                protein = protein,
                fat = fat,
                carbs = carbs,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}