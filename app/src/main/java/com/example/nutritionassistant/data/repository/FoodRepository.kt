package com.example.nutritionassistant.data.repository

import com.example.nutritionassistant.data.local.dao.FoodRecordDao
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 饮食记录 仓库
 * 作用：管理用户吃的所有食物记录（增删改查）
 */
// 单例：整个APP只创建一个仓库实例
@Singleton
class FoodRepository @Inject constructor(
    // 注入数据库操作类
    private val foodRecordDao: FoodRecordDao
) {

    /**
     * 插入一条饮食记录
     * 扫码保存、手动添加食物时都会调用
     */
    suspend fun insertFoodRecord(record: FoodRecordEntity) {
        foodRecordDao.insertFoodRecord(record)
    }

    /**
     * 查询【今天】吃的所有食物
     * 用于首页统计、饮食日记页面
     */
    suspend fun getTodayRecords(): List<FoodRecordEntity> {
        // 获取今天 00:00:00 的时间戳
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        // 获取明天 00:00:00 的时间戳
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        // 查询今天的所有记录
        return foodRecordDao.getRecordsByDate(startOfDay, endOfDay)
    }

    suspend fun deleteFoodRecord(record: FoodRecordEntity) {
        foodRecordDao.deleteFoodRecord(record)
    }

    suspend fun getAllRecords(): List<FoodRecordEntity> {
        return foodRecordDao.getAllRecords()
    }

    suspend fun getWeeklySummary(): List<DaySummary> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val endOfToday = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -6)  // 近7天，包含今天
        val startOf7DaysAgo = calendar.timeInMillis

        val records = foodRecordDao.getRecordsSince(startOf7DaysAgo)

        // 按天分组
        val grouped = records.groupBy { record ->
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = record.date
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        // 生成7天的列表，没有记录的补0
        val result = mutableListOf<DaySummary>()
        val current = java.util.Calendar.getInstance()
        current.timeInMillis = startOf7DaysAgo
        while (current.timeInMillis <= endOfToday) {
            val dayStart = current.timeInMillis
            val dayRecords = grouped[dayStart] ?: emptyList()
            result.add(DaySummary(
                date = dayStart,
                totalCalories = dayRecords.sumOf { it.calories.toDouble() }.toFloat(),
                totalProtein = dayRecords.sumOf { it.protein.toDouble() }.toFloat(),
                totalFat = dayRecords.sumOf { it.fat.toDouble() }.toFloat(),
                totalCarbs = dayRecords.sumOf { it.carbs.toDouble() }.toFloat()
            ))
            current.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    suspend fun updateFoodRecord(record: FoodRecordEntity) {
        foodRecordDao.update(record)
    }

    data class DaySummary(
        val date: Long,
        val totalCalories: Float,
        val totalProtein: Float,
        val totalFat: Float,
        val totalCarbs: Float
    )
}