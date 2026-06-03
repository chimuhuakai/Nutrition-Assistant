package com.example.nutritionassistant.ui.screen.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutritionassistant.data.local.entity.UserProfileEntity
import com.example.nutritionassistant.data.repository.FoodRepository
import com.example.nutritionassistant.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// 让 Hilt 管理这个 ViewModel，自动创建、自动注入依赖
@HiltViewModel
// @Inject：构造函数自动注入 UserRepository（仓库）
class HomeViewModel @Inject constructor(
    private val userRepo: UserRepository,  // 注入用户数据仓库，用来拿数据
    private val foodRepo: FoodRepository  // 注入食物数据仓库，用来拿数据
) : ViewModel() {

    // 私有的、可修改的数据（内部用）
    private val _userProfile = MutableLiveData<UserProfileEntity?>()
    // 公共的、只读的数据（页面用）
    val userProfile: LiveData<UserProfileEntity?> = _userProfile

    private val _todayNutrition = MutableLiveData<TodayNutrition>()
    val todayNutrition: LiveData<TodayNutrition> = _todayNutrition

    // 每日目标（根据用户档案计算）
    private val _dailyGoal = MutableLiveData<DailyGoal>()
    val dailyGoal: LiveData<DailyGoal> = _dailyGoal

    private val _nutritionAdvice = MutableLiveData<String>()
    val nutritionAdvice: LiveData<String> = _nutritionAdvice

    private val _mealCount = MutableLiveData<Int>()
    val mealCount: LiveData<Int> = _mealCount

    // ViewModel 创建时立刻执行
    init {
        loadProfile()  // 加载用户资料
        loadTodayNutrition()
    }

    // 从数据库加载用户资料
    private fun loadProfile() {
        // 启动协程（异步，不卡界面）
        viewModelScope.launch {
            // 从仓库获取用户资料
            val profile = userRepo.getProfile()
            // 更新数据 → 页面自动收到最新数据
            _userProfile.value = profile
            if (profile != null) {
                _dailyGoal.value = calculateGoal(profile)
            }
        }
    }

    private fun loadTodayNutrition() {
        viewModelScope.launch {
            val records = foodRepo.getTodayRecords()
            val totalCalories = records.sumOf { it.calories.toDouble() }.toFloat()
            val totalProtein = records.sumOf { it.protein.toDouble() }.toFloat()
            val totalFat = records.sumOf { it.fat.toDouble() }.toFloat()
            val totalCarbs = records.sumOf { it.carbs.toDouble() }.toFloat()
            val mealCount = records.size

            _mealCount.value = mealCount
            _todayNutrition.value = TodayNutrition(totalCalories, totalProtein, totalFat, totalCarbs)
        }
    }

    fun generateAdvice(nutrition: TodayNutrition, goal: DailyGoal) {
        val calRatio = nutrition.totalCalories / goal.calories
        val proteinRatio = nutrition.totalProtein / goal.protein
        val fatRatio = nutrition.totalFat / goal.fat
        val carbsRatio = nutrition.totalCarbs / goal.carbs
        val mealCount = _mealCount.value ?: 0

        val advice = buildOverallAdvice(calRatio, proteinRatio, fatRatio, carbsRatio, mealCount)
        _nutritionAdvice.value = advice
    }

    private fun buildOverallAdvice(
        calRatio: Float,
        proteinRatio: Float,
        fatRatio: Float,
        carbsRatio: Float,
        mealCount: Int
    ): String {
        // 判断各维度状态
        val calState = when {
            calRatio < 0.3f -> "low"
            calRatio in 0.3f..0.7f -> "mid"
            calRatio > 1.0f -> "over"
            else -> "high" // 0.7~1.0
        }
        val proteinState = when {
            proteinRatio < 0.3f -> "low"
            proteinRatio > 0.9f -> "good"
            else -> "ok"
        }
        val fatState = if (fatRatio > 0.8f) "high" else "ok"
        val carbsState = when {
            carbsRatio < 0.3f -> "low"
            carbsRatio > 0.9f -> "high"
            else -> "ok"
        }

        // 所有方面都很好时，直接返回夸奖
        if (calState == "mid" && proteinState == "good" && fatState == "ok" && carbsState == "ok" && mealCount >= 3) {
            return pickRandom(listOf(
                "今日营养摄入均衡，各方面都做得很好，继续保持！",
                "今天的饮食计划堪称完美，给自己点个赞！",
                "继续保持这种状态，离目标越来越近！"
            ))
        }

        // 开始构建连贯句子
        val parts = mutableListOf<String>()

        // 热量部分
        when (calState) {
            "low" -> parts.add(pickRandom(listOf(
                "热量摄入偏低，记得按时吃饭补充能量",
                "今天热量还没吃够，快去吃顿好的吧",
                "身体需要燃料，别饿着自己"
            )))
            "mid" -> parts.add("热量摄入适中，保持节奏")
            "over" -> parts.add("今日热量已超标，下一餐注意控制分量")
            "high" -> parts.add("热量摄入充足，接近目标")
        }

        // 蛋白质部分
        when (proteinState) {
            "low" -> parts.add(pickRandom(listOf(
                "蛋白质不足，可以加个鸡蛋或豆制品",
                "蛋白质偏低，来杯牛奶也不错",
                "建议补充蛋白质，瘦肉、豆腐都是好选择"
            )))
            "good" -> parts.add(pickRandom(listOf(
                "蛋白质达标，肌肉修复中",
                "蛋白质充足，力量满满"
            )))
        }

        // 脂肪部分
        if (fatState == "high") {
            parts.add(pickRandom(listOf(
                "脂肪摄入偏高，建议下一餐清淡些",
                "今天的油脂有点多，炒菜少放油吧",
                "注意脂肪摄入，优选蒸煮方式烹饪"
            )))
        }

        // 碳水部分
        when (carbsState) {
            "low" -> parts.add(pickRandom(listOf(
                "碳水摄入偏低，别忘了吃主食",
                "米饭、面条别忘啦，小心低血糖"
            )))
            "high" -> parts.add("碳水充足，体力有保障")
        }

        // 餐数部分
        when {
            mealCount == 0 -> parts.add("今天还没记录饮食，快去添加第一餐吧")
            mealCount in 1..2 -> parts.add("目前只记录了${mealCount}餐，完整记录能让建议更精准")
            mealCount >= 4 -> parts.add("你记录得很认真，继续加油")
        }

        // 用逗号连接，最后加句号
        return parts.joinToString("，") + "。"
    }

    // 辅助方法：从列表中随机选一条
    private fun pickRandom(list: List<String>): String {
        return list.random()
    }
    fun refresh() {
        loadProfile()
        loadTodayNutrition()
    }

    private fun calculateGoal(profile: UserProfileEntity): DailyGoal {
        val baseCal = when (profile.gender) {
            "male" -> 10*profile.weight + 6.25*profile.height - 5*profile.age + 5.0f
            else -> 10*profile.weight + 6.25*profile.height - 5*profile.age - 161.0f
        }
        val modifier = when (profile.goal) {
            "lose_fat" -> 0.8f
            "gain_muscle" -> 1.2f
            else -> 1.0f
        }
        val targetCal: Double = baseCal * modifier
        return DailyGoal(
            calories = targetCal.toFloat(),
            protein = profile.weight * 1.5f,   // g per kg
            fat = (targetCal * 0.25f / 9f).toFloat(),      // 25% from fat
            carbs = (targetCal * 0.55f / 4f).toFloat()     // 55% from carbs
        )
    }
}


data class TodayNutrition(
    val totalCalories: Float,
    val totalProtein: Float,
    val totalFat: Float,
    val totalCarbs: Float
)

data class DailyGoal(
    val calories: Float = 2000f,
    val protein: Float = 60f,
    val fat: Float = 55f,
    val carbs: Float = 250f
)