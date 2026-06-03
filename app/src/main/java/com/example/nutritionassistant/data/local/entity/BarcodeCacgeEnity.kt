package com.example.nutritionassistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barcode_cache")
data class BarcodeCacheEntity(
    @PrimaryKey val barcode: String,  // 条形码作为主键
    val foodName: String,              // 食品名称
    val nutritionJson: String,        // 营养信息（JSON格式）
    val lastUpdated: Long,             // 最后更新时间戳
    val calories: Float = 0f,    // 新增
    val protein: Float = 0f,     // 新增
    val fat: Float = 0f,         // 新增
    val carbs: Float = 0f,       // 新增
)
