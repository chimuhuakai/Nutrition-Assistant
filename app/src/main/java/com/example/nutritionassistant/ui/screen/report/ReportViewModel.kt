package com.example.nutritionassistant.ui.screen.report

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutritionassistant.data.local.ReminderPreferences
import com.example.nutritionassistant.data.local.entity.UserProfileEntity
import com.example.nutritionassistant.data.repository.AiRepository
import com.example.nutritionassistant.data.repository.FoodRepository
import com.example.nutritionassistant.data.repository.FoodRepository.DaySummary
import com.example.nutritionassistant.data.repository.UserRepository
import com.example.nutritionassistant.ui.screen.home.DailyGoal
import com.example.nutritionassistant.ui.screen.home.HomeViewModel
import com.github.mikephil.charting.utils.Utils.init
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val userRepo: UserRepository,
    private val aiRepo: AiRepository,
    private val reminderPrefs: ReminderPreferences
) : ViewModel() {

    private val _weeklyData = MutableLiveData<List<DaySummary>>()
    val weeklyData: LiveData<List<DaySummary>> = _weeklyData

    private val _dailyGoal = MutableLiveData<DailyGoal>()
    val dailyGoal: LiveData<DailyGoal> = _dailyGoal

    private val _aiAnalysis = MutableLiveData<String>()
    val aiAnalysis: LiveData<String> = _aiAnalysis

    init {
        loadWeeklyData()
        loadDailyGoal()
        loadSavedAiReport()   // 加载持久化的 AI 报告
    }

    private fun loadDailyGoal() {
        viewModelScope.launch {
            val profile = userRepo.getProfile()
            if (profile != null) {
                // 用 Mifflin-St Jeor 公式计算基础代谢率 (BMR)
                val bmr = if (profile.gender == "male") {
                    10 * profile.weight + 6.25f * profile.height - 5 * profile.age + 5f
                } else {
                    10 * profile.weight + 6.25f * profile.height - 5 * profile.age - 161f
                }

                // 根据目标调整热量
                val modifier = when (profile.goal) {
                    "lose_fat" -> 0.8f      // 减脂：热量缺口 20%
                    "gain_muscle" -> 1.2f  // 增肌：热量盈余 20%
                    else -> 1.0f            // 维持：不变
                }
                val targetCal = bmr * modifier

                _dailyGoal.value = DailyGoal(
                    calories = targetCal,
                    protein = profile.weight * 1.5f,
                    fat = targetCal * 0.25f / 9f,
                    carbs = targetCal * 0.55f / 4f
                )
            }
        }
    }

    fun loadWeeklyData() {
        viewModelScope.launch {
            _weeklyData.value = foodRepo.getWeeklySummary()
            // 数据加载完成后，触发 AI 分析
        }
    }

    fun requestAiAnalysis() {
        viewModelScope.launch {
            val data = _weeklyData.value ?: return@launch
            if (data.isEmpty()) return@launch

            val goal = _dailyGoal.value ?: return@launch
            val profile = userRepo.getProfile() ?: return@launch

            _aiAnalysis.value = "正在生成分析报告..."
            try {
                val prompt = buildAnalysisPrompt(data, goal, profile)
                val response = aiRepo.ask(prompt)
                _aiAnalysis.value = response
                reminderPrefs.saveAiReport(response)
            } catch (e: Exception) {
                _aiAnalysis.value = "AI 分析暂时不可用：${e.message}"
            }
        }
    }


    private fun buildAnalysisPrompt(
        data: List<DaySummary>,
        goal: DailyGoal,
        profile: UserProfileEntity
    ): String {
        val avgCal = data.map { it.totalCalories }.average().toInt()
        val avgProtein = data.map { it.totalProtein }.average().toInt()
        val avgFat = data.map { it.totalFat }.average().toInt()
        val avgCarbs = data.map { it.totalCarbs }.average().toInt()

        // 统计超标/不足天数
        val overCalDays = data.count { it.totalCalories > goal.calories }
        val lowProteinDays = data.count { it.totalProtein < goal.protein * 0.8 }

        return """
你是一位专业营养师。请根据以下用户的近7天饮食数据，给出简洁的评估和建议（控制在150字以内）。

用户档案：${if (profile.gender == "male") "男性" else "女性"}，${profile.age}岁，${profile.weight}kg，${profile.height}cm
目标：${when(profile.goal) { "lose_fat" -> "减脂" "gain_muscle" -> "增肌" else -> "维持体重" }}

每日目标：热量 ${goal.calories.toInt()} kcal，蛋白质 ${goal.protein.toInt()}g，脂肪 ${goal.fat.toInt()}g，碳水 ${goal.carbs.toInt()}g

近7天平均摄入：热量 ${avgCal} kcal，蛋白质 ${avgProtein}g，脂肪 ${avgFat}g，碳水 ${avgCarbs}g
热量超标天数：$overCalDays 天，蛋白质不足天数：$lowProteinDays 天

请分析用户的营养摄入情况，并给出针对性的改善建议。直接回复，不要用列表格式。
        """.trimIndent()
    }

    private fun loadSavedAiReport() {
        viewModelScope.launch {
            val saved = reminderPrefs.getAiReport()
            if (saved.isNotEmpty()) {
                _aiAnalysis.value = saved
            }
        }
    }


}