package com.example.nutritionassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nutritionassistant.data.local.entity.BarcodeCacheEntity

@Dao
interface BarcodeCacheDao {
    @Query("SELECT * FROM barcode_cache WHERE barcode = :barcode")
    suspend fun getBarcodeCache(barcode: String): BarcodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcodeCache(cache: BarcodeCacheEntity)
}