package com.example.nutritionassistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food")
data class FoodRecordEntity(
    @PrimaryKey (autoGenerate = true) val id: Int = 0,
    val name: String,// 食品名称
    val mealType: String,// 用餐类型（早餐/午餐/晚餐）
    val calories: Float,// 卡路里
    val protein: Float,// 蛋白质
    val fat: Float,// 脂肪
    val carbs: Float,// 袳水化合物
    val date: Long,// 日期
    val imageUri: String? = null,// 图片URI（可选）
    val barcode: String? = null// 条形码（可选）
)
