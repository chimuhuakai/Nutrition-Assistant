package com.example.nutritionassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity

@Dao
interface FoodRecordDao {
    @Query("SELECT * FROM food WHERE date BETWEEN :startOfDay AND :endOfDay")
    suspend fun getRecordsByDate(startOfDay: Long, endOfDay: Long): List<FoodRecordEntity>

    @Insert
    suspend fun insertFoodRecord(record: FoodRecordEntity)

    @Delete
    suspend fun deleteFoodRecord(record: FoodRecordEntity)

    @Query("SELECT * FROM food ORDER BY date DESC")
    suspend fun getAllRecords(): List<FoodRecordEntity>

    @Query("SELECT * FROM food WHERE date >= :startTime ORDER BY date ASC")
    suspend fun getRecordsSince(startTime: Long): List<FoodRecordEntity>

    @Update
    suspend fun update(record: FoodRecordEntity)
}