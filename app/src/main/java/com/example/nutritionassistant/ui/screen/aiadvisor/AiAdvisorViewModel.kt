package com.example.nutritionassistant.ui.screen.aiadvisor

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import com.example.nutritionassistant.data.local.entity.UserProfileEntity
import com.example.nutritionassistant.data.remote.api.TianApiService
import com.example.nutritionassistant.data.repository.AiRepository
import com.example.nutritionassistant.data.repository.FoodRepository
import com.example.nutritionassistant.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiAdvisorViewModel @Inject constructor(
    private val aiRepo: AiRepository,
    private val userRepo: UserRepository,
    private val foodRepo: FoodRepository,
    private val tianApiService: TianApiService   // 新增：用于精确营养查询
) : ViewModel() {

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _adviceList = MutableLiveData<List<AdviceItem>>()
    val adviceList: LiveData<List<AdviceItem>> = _adviceList

    private val _adoptResult = MutableLiveData<Boolean>()
    val adoptResult: LiveData<Boolean> = _adoptResult

    private val _isLoadMore = MutableLiveData(false)
    val isLoadMore: LiveData<Boolean> = _isLoadMore

    private val chatHistory = mutableListOf<Pair<String, String>>()
    private var lastQuestion: String = ""

    // ────────────────── 对外公开方法 ──────────────────

    fun askQuestion(question: String) {
        viewModelScope.launch {
            _isLoadMore.value = false
            _loading.value = true
            lastQuestion = question
            try {
                val data = getUserData()
                val blacklist = userRepo.getBlacklist()
                val prompt = buildNextMealPrompt(data, question, blacklist)
                val response = aiRepo.ask(prompt)
                val items = parseSingleMealResponse(response)
                // 精确营养查询 + 对齐目标
                var enriched = enrichAdviceItems(items)
                enriched = fitToTarget(enriched, NutritionTarget(data.remainingCal))
                _adviceList.value = enriched
                saveChatHistory(question, response)
            } catch (e: Exception) {
                Log.e("AiAdvisor", "AI请求失败", e)
                _adviceList.value = listOf(AdviceItem("请求失败", "${e.message}", "", 0f, 0f, 0f, 0f))
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMore() {
        val currentItems = _adviceList.value.orEmpty()
        if (currentItems.isEmpty() || lastQuestion.isBlank()) return
        viewModelScope.launch {
            _isLoadMore.value = true
            _loading.value = true
            try {
                val data = getUserData()
                val prompt = buildMorePrompt(data, lastQuestion, currentItems)
                val response = aiRepo.ask(prompt)
                val newItems = parseSingleMealResponse(response)
                var enriched = enrichAdviceItems(newItems)
                _adviceList.value = currentItems + enriched
            } catch (e: Exception) {
                Log.e("AiAdvisor", "加载更多失败", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun requestFullDayPlan(question: String) {
        viewModelScope.launch {
            _isLoadMore.value = false
            _loading.value = true
            lastQuestion = question
            try {
                val data = getUserData()
                val blacklist = userRepo.getBlacklist()
                val prompt = buildFullDayPrompt(data, question, blacklist)
                val response = aiRepo.ask(prompt)
                val items = parseFullDayResponse(response)
                // 精确营养查询 + 全天对齐目标
                var enriched = enrichAdviceItems(items)
                enriched = fitToTarget(enriched, NutritionTarget(
                    calories = data.remainingCal,
                    protein = data.profile.weight * 1.5f,
                    fat = data.targetCal * 0.25f / 9f,
                    carbs = data.targetCal * 0.55f / 4f
                ))
                _adviceList.value = enriched
            } catch (e: Exception) {
                Log.e("AiAdvisor", "全天规划失败", e)
                _adviceList.value = listOf(AdviceItem("请求失败", "${e.message}", "", 0f, 0f, 0f, 0f))
            } finally {
                _loading.value = false
            }
        }
    }

    fun adoptAdvice(item: AdviceItem, mealType: String,mode: Int) {
        viewModelScope.launch {
            foodRepo.insertFoodRecord(FoodRecordEntity(
                name = item.foodName,
                mealType = mealType,
                calories = item.calories,
                protein = item.protein,
                fat = item.fat,
                carbs = item.carbs,
                date = System.currentTimeMillis()
            ))
            if(mode == AiAdvisorFragment.MODE_NEXT_MEAL) _adoptResult.value = true
        }
    }



    fun clearAdviceList() { _adviceList.value = emptyList() }


    // ────────────────── 内部数据类 ──────────────────

    private data class UserData(
        val profile: UserProfileEntity,
        val todayRecords: List<FoodRecordEntity>,
        val targetCal: Float,
        val remainingCal: Float,
        val todayCal: Float,
        val todayProtein: Float,
        val todayFat: Float,
        val todayCarbs: Float
    )

    private data class NutritionTarget(
        val calories: Float = 0f,
        val protein: Float = 0f,
        val fat: Float = 0f,
        val carbs: Float = 0f
    )

    // ────────────────── 数据获取 ──────────────────

    private suspend fun getUserData(): UserData {
        val profile = userRepo.getProfile() ?: UserProfileEntity(
            name = "用户", age = 25, gender = "male",
            weight = 65f, height = 170f, goal = "maintain"
        )
        val todayRecords = foodRepo.getTodayRecords()
        val todayCal = todayRecords.sumOf { it.calories.toDouble() }.toFloat()
        val todayProtein = todayRecords.sumOf { it.protein.toDouble() }.toFloat()
        val todayFat = todayRecords.sumOf { it.fat.toDouble() }.toFloat()
        val todayCarbs = todayRecords.sumOf { it.carbs.toDouble() }.toFloat()
        val baseCal = if (profile.gender == "male") 2200f else 1800f
        val goalModifier = when (profile.goal) {
            "lose_fat" -> 0.8f; "gain_muscle" -> 1.2f; else -> 1.0f
        }
        val targetCal = baseCal * goalModifier
        val remainingCal = targetCal - todayCal
        return UserData(profile, todayRecords, targetCal, remainingCal, todayCal, todayProtein, todayFat, todayCarbs)
    }

    // ────────────────── Prompt 构建（仅要求菜名） ──────────────────

    private fun buildNextMealPrompt(data: UserData, question: String, blacklist: List<String>): String {
        val blacklistText = if (blacklist.isNotEmpty()) blacklist.joinToString("、") else "无"
        val historyText = if (chatHistory.isNotEmpty()) {
            chatHistory.takeLast(4).joinToString("\n") { "${it.first}: ${it.second}" }
        } else "无对话历史"
        return """
你是一位专业营养师。以下是最近的对话历史：$historyText
用户问了以下问题："$question"

用户档案：${data.profile.name}，${data.profile.age}岁，${if (data.profile.gender == "male") "男性" else "女性"}，体重${data.profile.weight}kg，身高${data.profile.height}cm，目标：${
            when (data.profile.goal) { "lose_fat" -> "减脂"; "gain_muscle" -> "增肌"; else -> "保持体重" }
        }
忌口食材：$blacklistText

今日已摄入：${data.todayCal.toInt()} kcal，蛋白质 ${data.todayProtein.toInt()}g，脂肪 ${data.todayFat.toInt()}g，碳水 ${data.todayCarbs.toInt()}g
每日目标热量：${data.targetCal.toInt()} kcal，剩余：${data.remainingCal.toInt()} kcal

请推荐1-3道适合用户的菜品，热量不超过剩余额度。
严格按JSON格式返回，只需菜名和推荐理由：
[{"foodName":"菜名","reason":"理由"}]
        """.trimIndent()
    }

    private fun buildMorePrompt(data: UserData, question: String, existing: List<AdviceItem>): String {
        val existingNames = existing.joinToString("、") { it.foodName }
        return """
你是一位专业营养师。用户之前问过："$question"，已推荐：$existingNames。
现在请再推荐2-3道不同的类似菜品，热量不超${data.remainingCal.toInt()} kcal，避开已有菜品。
严格按JSON返回：[{"foodName":"菜名","reason":"理由"}]
        """.trimIndent()
    }

    private fun buildFullDayPrompt(data: UserData, question: String, blacklist: List<String>): String {
        val blacklistText = if (blacklist.isNotEmpty()) blacklist.joinToString("、") else "无"
        val userDesc = if (question.isNotBlank()) "用户特殊要求：$question" else ""
        val proteinGoal = data.profile.weight * 1.5f
        val fatGoal = data.targetCal * 0.25f / 9f
        val carbsGoal = data.targetCal * 0.55f / 4f
        return """
你是一位资深营养师，请为用户规划一整天的饮食（早餐、午餐、晚餐）。
每一餐必须包含：
- 一份主食（午饭晚饭必须是米饭）
- 1～3道菜品（荤素搭配，不需要每餐都有汤）

所有食物均按正常单人份量（无需标注克数）。

用户档案：
${if (data.profile.gender == "male") "男性" else "女性"}，${data.profile.age}岁，${data.profile.weight}kg，${data.profile.height}cm
目标：${when(data.profile.goal) { "lose_fat" -> "减脂" "gain_muscle" -> "增肌" else -> "保持体重" }}
忌口食材：$blacklistText
$userDesc

今日已摄入：热量 ${data.todayCal.toInt()} kcal，蛋白质 ${data.todayProtein.toInt()}g，脂肪 ${data.todayFat.toInt()}g，碳水 ${data.todayCarbs.toInt()}g
全天总目标热量：${data.targetCal.toInt()} kcal
全天剩余热量：${data.remainingCal.toInt()} kcal
全天蛋白质目标：${proteinGoal.toInt()}g，脂肪目标：${fatGoal.toInt()}g，碳水目标：${carbsGoal.toInt()}g

请规划三餐，让全天总热量尽量接近剩余热量 ${data.remainingCal.toInt()} kcal，总蛋白质接近 ${proteinGoal.toInt()}g，总脂肪接近 ${fatGoal.toInt()}g，总碳水接近 ${carbsGoal.toInt()}g。

请严格按以下 JSON 格式返回，不要包含任何其他文字：
{
  "meals": [
    {"mealType":"breakfast","staples":[{"foodName":"主食名"}],"dishes":[{"foodName":"菜品名"}]},
    {"mealType":"lunch","staples":[...],"dishes":[...]},
    {"mealType":"dinner","staples":[...],"dishes":[...]}
  ]
}
注意：每个菜品只需提供名字，正常单人份，不需要营养数值。
        """.trimIndent()
    }

    // ────────────────── AI 响应解析（仅提取菜名） ──────────────────

    private fun parseSingleMealResponse(response: String): List<AdviceItem> {
        try {
            val clean = response.replace("```json", "").replace("```", "").trim()
            val start = clean.indexOf('[')
            val end = clean.lastIndexOf(']')
            if (start == -1 || end == -1) return emptyList()
            val arr = org.json.JSONArray(clean.substring(start, end + 1))
            return (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                AdviceItem(
                    foodName = obj.optString("foodName", "未知"),
                    reason = obj.optString("reason", ""),
                    nutritionText = "", calories = 0f, protein = 0f, fat = 0f, carbs = 0f
                )
            }
        } catch (e: Exception) { return emptyList() }
    }

    private fun parseFullDayResponse(response: String): List<AdviceItem> {
        try {
            val clean = response.replace("```json", "").replace("```", "").trim()
            val start = clean.indexOf('{')
            val end = clean.lastIndexOf('}')
            if (start == -1 || end == -1) return emptyList()
            val root = org.json.JSONObject(clean.substring(start, end + 1))
            val meals = root.optJSONArray("meals") ?: return emptyList()
            val items = mutableListOf<AdviceItem>()
            for (i in 0 until meals.length()) {
                val meal = meals.getJSONObject(i)
                val mealType = meal.optString("mealType", "")
                val staples = meal.optJSONArray("staples")
                if (staples != null) for (j in 0 until staples.length()) {
                    items.add(AdviceItem(
                        foodName = staples.getJSONObject(j).optString("foodName", "未知"),
                        reason = "${mealType.toMealName()}主食", mealType = mealType,
                        nutritionText = "", calories = 0f, protein = 0f, fat = 0f, carbs = 0f
                    ))
                }
                val dishes = meal.optJSONArray("dishes")
                if (dishes != null) for (j in 0 until dishes.length()) {
                    items.add(AdviceItem(
                        foodName = dishes.getJSONObject(j).optString("foodName", "未知"),
                        reason = "${mealType.toMealName()}菜品", mealType = mealType,
                        nutritionText = "", calories = 0f, protein = 0f, fat = 0f, carbs = 0f
                    ))
                }
            }
            return items
        } catch (e: Exception) { return emptyList() }
    }

    private fun String.toMealName() = when(this) {
        "breakfast" -> "早餐"; "lunch" -> "午餐"; "dinner" -> "晚餐"; else -> this
    }

    // ────────────────── 精确营养查询 ──────────────────

    private suspend fun enrichAdviceItems(items: List<AdviceItem>): List<AdviceItem> {
        val result = mutableListOf<AdviceItem>()
        for (item in items) {
            var cal = 0f; var pro = 0f; var fat = 0f; var carbs = 0f

            // 先尝试营养成分 API
            try {
                val resp = tianApiService.getNutrient(item.foodName)
                val nutrient = resp.result?.list?.firstOrNull()
                cal = nutrient?.calorie?.toFloatOrNull() ?: 0f
                pro = nutrient?.protein?.toFloatOrNull() ?: 0f
                fat = nutrient?.fat?.toFloatOrNull() ?: 0f
                carbs = nutrient?.carbohydrate?.toFloatOrNull() ?: 0f
            } catch (_: Exception) { }

            // 如果 API 没查到有效数据，用 AI 兜底
            if (cal <= 0f && pro <= 0f && fat <= 0f && carbs <= 0f) {
                askAiForIngredientNutrition(item.foodName)?.let { aiData ->
                    cal = aiData.cal; pro = aiData.pro; fat = aiData.fat; carbs = aiData.carbs
                }
            }

            result.add(item.copy(
                calories = cal,
                protein = pro,
                fat = fat,
                carbs = carbs,
                nutritionText = if (cal > 0 || pro > 0 || fat > 0 || carbs > 0)
                    "${cal.toInt()} kcal | 蛋白${pro.toInt()}g | 脂肪${fat.toInt()}g | 碳水${carbs.toInt()}g"
                else ""
            ))
            delay(600)   // 控制请求频率，避免触发限制
        }
        return result
    }


    // ────────────────── 自动对齐目标 ──────────────────

    /**
     * 根据热量目标剔除多余的菜品，使总热量不超过目标。
     * 保持菜品顺序，从末尾（通常 AI 推荐的次要菜品）开始剔除。
     * 同时对全天规划也尝试对齐蛋白质等，但主要通过热量控制。
     */
    private fun fitToTarget(items: List<AdviceItem>, target: NutritionTarget): List<AdviceItem> {
        if (items.isEmpty() || target.calories <= 0) return items

        val totalCal = items.sumOf { it.calories.toDouble() }.toFloat()
        if (totalCal <= target.calories) return items

        // 计算缩放比例（总热量超标则按比例缩减）
        val ratio = target.calories / totalCal
        return items.map { item ->
            val newCal = item.calories * ratio
            val newPro = item.protein * ratio
            val newFat = item.fat * ratio
            val newCarbs = item.carbs * ratio
            item.copy(
                calories = newCal,
                protein = newPro,
                fat = newFat,
                carbs = newCarbs,
                nutritionText = "${newCal.toInt()} kcal | 蛋白${newPro.toInt()}g | 脂肪${newFat.toInt()}g | 碳水${newCarbs.toInt()}g",
                reason = "${item.reason}（份量已自动调整为 ${(ratio * 100).toInt()}%）"
            )
        }
    }

    // ────────────────── 历史记录 ──────────────────

    private fun saveChatHistory(question: String, response: String) {
        chatHistory.add("user" to question)
        chatHistory.add("assistant" to response)
        if (chatHistory.size > 20) {
            chatHistory.removeAt(0)
            chatHistory.removeAt(0)
        }
    }

    private suspend fun askAiForIngredientNutrition(ingredientName: String): FullNutritionData? {
        return try {
            val prompt = """
            你是一位专业营养师。请提供一道菜"$ingredientName"（正常单人份）的营养成分估算。
            直接返回 JSON：{"calories": 数值, "protein": 数值, "fat": 数值, "carbs": 数值}
            只返回 JSON，不要其他文字。
        """.trimIndent()
            val response = aiRepo.ask(prompt)
            val cal = Regex(""""calories":\s*([\d.]+)""").find(response)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val pro = Regex(""""protein":\s*([\d.]+)""").find(response)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val fat = Regex(""""fat":\s*([\d.]+)""").find(response)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val carbs = Regex(""""carbs":\s*([\d.]+)""").find(response)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            FullNutritionData(cal, pro, fat, carbs)
        } catch (e: Exception) {
            Log.e("AiAdvisor", "AI 兜底失败：$ingredientName", e)
            null
        }
    }

    private data class FullNutritionData(val cal: Float, val pro: Float, val fat: Float, val carbs: Float)
}

// AdviceItem 数据类（保持不变）
data class AdviceItem(
    val foodName: String,
    val reason: String,
    val nutritionText: String,
    val calories: Float = 0f,
    val protein: Float = 0f,
    val fat: Float = 0f,
    val carbs: Float = 0f,
    val mealType: String = ""
)