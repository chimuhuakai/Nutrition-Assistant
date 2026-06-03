package com.example.nutritionassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.nutritionassistant.data.local.dao.BarcodeCacheDao
import com.example.nutritionassistant.data.local.dao.FoodRecordDao
import com.example.nutritionassistant.data.local.dao.UserProfileDao
import com.example.nutritionassistant.data.local.entity.BarcodeCacheEntity
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import com.example.nutritionassistant.data.local.entity.UserProfileEntity

@Database(
    entities = [FoodRecordEntity::class, UserProfileEntity::class, BarcodeCacheEntity::class],
    version = 4
)
abstract class Database : RoomDatabase() {
    abstract fun foodRecordDao(): FoodRecordDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun barcodeCacheDao(): BarcodeCacheDao
}