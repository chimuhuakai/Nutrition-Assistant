package com.example.nutritionassistant.ui.screen.diary

import DiaryGroup
import android.R.attr.prompt
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import com.example.nutritionassistant.data.remote.api.TianApiService
import com.example.nutritionassistant.data.remote.dto.NutrientItem
import com.example.nutritionassistant.data.repository.AiRepository
import com.example.nutritionassistant.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val tianApiService: TianApiService,
    private val aiRepo: AiRepository  // AI 仓库
) : ViewModel() {

    private val _records = MutableLiveData<List<DiaryGroup>>()
    val records: LiveData<List<DiaryGroup>> = _records

    private val _deleteResult = MutableLiveData<Boolean>()
    val deleteResult: LiveData<Boolean> = _deleteResult

    init {
        loadRecords()
    }

    /**
     * 加载所有饮食记录，并按【日期分组 + 排序】
     */
    fun loadRecords() {
        // 启动协程（异步执行，不卡界面）
        viewModelScope.launch {

            // 1. 从仓库（数据库）获取【所有】饮食记录
            val all = foodRepo.getAllRecords()

            // 2. 按【日期】分组：同一天的记录放在一组
            val grouped = all.groupBy { record ->
                // 把记录的时间戳转成日历
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = record.date

                // 关键：把 时、分、秒、毫秒 清零
                // 作用：确保 2025-05-26 08:00 和 2025-05-26 12:00 会被分到同一天
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                // 返回【当天0点的时间戳】作为分组的key
                calendar.timeInMillis
            }

            // 3. 把分组后的数据转成 DiaryGroup 对象
            // 并按【日期倒序】排列：最新的日期排在最上面
            val sortedGroups = grouped.map { (date, items) ->
                DiaryGroup(date = date, items = items)
            }.sortedByDescending { it.date }

            // 4. 把分组+排序好的数据交给界面显示
            _records.value = sortedGroups
        }
    }

    fun deleteRecord(record: FoodRecordEntity) {
        viewModelScope.launch {
            foodRepo.deleteFoodRecord(record)
            _deleteResult.value = true
            loadRecords()
        }
    }

    private val _queryResult = MutableLiveData<NutrientItem?>()
    val queryResult: LiveData<NutrientItem?> = _queryResult

    fun queryFood(foodName: String) {
        viewModelScope.launch {
            try {
                Log.d("DiaryVM", "========== 开始查询: $foodName ==========")
                // 1. 天然食材
                val nutrientResp = tianApiService.getNutrient(foodName)
                Log.d(
                    "DiaryVM",
                    "天然食材API返回：code=${nutrientResp.code}, msg=${nutrientResp.msg}"
                )
                val item = nutrientResp.result?.list?.firstOrNull()
                if (item != null && !item.calorie.isNullOrEmpty()) {
                    Log.d("DiaryVM", "✅ 天然食材找到：${item.name}，热量=${item.calorie}")
                    _queryResult.value = item
                    return@launch
                }

                // 2. 菜谱查询
                Log.d("DiaryVM", "天然食材未命中，尝试菜谱查询...")
                val recipeResp = tianApiService.getRecipe(foodName)
                Log.d("DiaryVM", "菜谱API返回：code=${recipeResp.code}, msg=${recipeResp.msg}")
                val recipe = recipeResp.result?.list?.firstOrNull()
                if (recipe != null && !recipe.yuanliao.isNullOrBlank()) {
                    Log.d("DiaryVM", "✅ 菜谱找到：${recipe.cpName}，原料：${recipe.yuanliao}")
                    val total = calculateRecipeNutrition(recipe.yuanliao)
                    if (total != null) {
                        Log.d(
                            "DiaryVM",
                            "✅ 原料营养计算成功：热量=${total.calories}，蛋白=${total.protein}"
                        )
                        _queryResult.value = NutrientItem(
                            name = recipe.cpName ?: foodName,
                            calorie = total.calories.toFloat().toString(),
                            protein = total.protein.toFloat().toString(),
                            fat = total.fat.toFloat().toString(),
                            carbohydrate = total.carbs.toFloat().toString()
                        )
                    } else {
                        Log.e("DiaryVM", "❌ 原料营养计算失败")
                        _queryResult.value = null
                    }
                } else {
                    // 3. AI 兜底
                    Log.d("DiaryVM", "菜谱无结果，尝试 AI 分析原料...")
                    val aiIngredients = queryIngredientsFromAI(foodName)
                    if (aiIngredients != null && aiIngredients.isNotEmpty()) {
                        Log.d("DiaryVM", "AI 解析出原料: $aiIngredients")
                        val total = calculateNutritionFromIngredients(aiIngredients)
                        if (total != null) {
                            _queryResult.value = NutrientItem(
                                name = foodName,
                                calorie = total.calories.toFloat().toString(),
                                protein = total.protein.toFloat().toString(),
                                fat = total.fat.toFloat().toString(),
                                carbohydrate = total.carbs.toFloat().toString()
                            )
                        } else {
                            _queryResult.value = null
                        }
                    } else {
                        Log.e("DiaryVM", "AI 也无法分析出原料")
                        _queryResult.value = null
                    }
                }
            } catch (e: Exception) {
                Log.e("DiaryVM", "❌ 查询异常：${e.message}", e)
                _queryResult.value = null
            }
        }
    }

    fun addManualFood(foodName: String, mealType: String, date: Long, item: NutrientItem, grams: Float) {
        viewModelScope.launch {
            val ratio = grams / 100f
            val record = FoodRecordEntity(
                name = foodName,
                mealType = mealType,
                calories = (item.calorie?.toFloatOrNull() ?: 0f) * ratio,
                protein = (item.protein?.toFloatOrNull() ?: 0f) * ratio,
                fat = (item.fat?.toFloatOrNull() ?: 0f) * ratio,
                carbs = (item.carbohydrate?.toFloatOrNull() ?: 0f) * ratio,
                date = date
            )
            foodRepo.insertFoodRecord(record)
            loadRecords()
        }
    }

    // ================== AI 相关方法 ==================
    private suspend fun queryIngredientsFromAI(dishName: String): List<Pair<String, Double>>? {
        return try {
            val prompt = """
你是一位专业营养师。请分析菜品"$dishName"的完整配方成分，包括主要食材、调味料和烹饪用油，并给出每100克成品中各成分的大致用量（克）。
特别注意：油炸类食品必须包含烹饪过程中吸收的油脂；炒菜类必须包含炒制用油；调味料如盐、糖、酱油等也需要计入。
请直接返回一个JSON数组，格式如下：
[{"name":"成分名称","grams":每100克成品中的重量}, ...]
只返回JSON，不要包含其他文字。
""".trimIndent()

            val response = aiRepo.ask(prompt)
            Log.d("DiaryVM", "AI 返回内容: $response")
            parseAIResponse(response)
        } catch (e: Exception) {
            Log.e("DiaryVM", "AI 分析原料失败", e)
            null
        }
    }

    private fun parseAIResponse(response: String): List<Pair<String, Double>>? {
        try {
            // 1. 清理 AI 返回的内容
            val clean = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // 2. 提取 JSON 数组部分
            val start = clean.indexOf('[')
            val end = clean.lastIndexOf(']')
            if (start == -1 || end == -1 || end <= start) {
                Log.e("DiaryVM", "AI 返回中未找到 JSON 数组")
                return null
            }
            val jsonArrayStr = clean.substring(start, end + 1)
            Log.d("DiaryVM", "提取的 JSON 数组: $jsonArrayStr")

            // 3. 用 Android 内置的 JSONArray 解析
            val jsonArray = org.json.JSONArray(jsonArrayStr)
            val result = mutableListOf<Pair<String, Double>>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                val grams = obj.optDouble("grams", -1.0)

                if (name.isNotEmpty() && grams > 0) {
                    result.add(name to grams)
                    Log.d("DiaryVM", "AI 解析: $name -> ${grams}g")
                }
            }

            return if (result.isEmpty()) null else result
        } catch (e: Exception) {
            Log.e("DiaryVM", "解析 AI JSON 失败: ${e.message}", e)
            return null
        }
    }

    private suspend fun calculateNutritionFromIngredients(ingredients: List<Pair<String, Double>>): NutritionSum? {
        var totalCal = 0.0;
        var totalProtein = 0.0;
        var totalFat = 0.0;
        var totalCarbs = 0.0

        for ((name, grams) in ingredients) {
            try {
                val queryName = aliasMap[name] ?: name
                val resp = tianApiService.getNutrient(queryName)
                Log.d("DiaryVM", "$name ($queryName) 查询返回: code=${resp.code}")

                var item = resp.result?.list?.firstOrNull()

                // 营养成分表查不到 → AI 兜底
                if (item == null || item.protein == null) {
                    Log.w("DiaryVM", "$name 营养成分表未找到，尝试AI查询...")
                    item = askAiForIngredientNutrition(queryName)
                }

                if (item != null) {
                    val cal100g = item.calorie?.toDoubleOrNull() ?: 0.0
                    val pro100g = item.protein?.toDoubleOrNull() ?: 0.0
                    val fat100g = item.fat?.toDoubleOrNull() ?: 0.0
                    val carbs100g = item.carbohydrate?.toDoubleOrNull() ?: 0.0

                    val ratio = grams / 100.0
                    totalCal += cal100g * ratio
                    totalProtein += pro100g * ratio
                    totalFat += fat100g * ratio
                    totalCarbs += carbs100g * ratio

                    Log.d(
                        "DiaryVM",
                        "$name 贡献: 热量=${cal100g * ratio}, 蛋白=${pro100g * ratio}, 脂肪=${fat100g * ratio}, 碳水=${carbs100g * ratio}"
                    )
                } else {
                    Log.w("DiaryVM", "$name AI也未能找到营养数据")
                }
                delay(500)
            } catch (e: Exception) {
                Log.e("DiaryVM", "查询原料 $name 失败", e)
            }
        }

        Log.d(
            "DiaryVM",
            "累加结果: 热量=$totalCal, 蛋白=$totalProtein, 脂肪=$totalFat, 碳水=$totalCarbs"
        )
        return if (totalCal == 0.0 && totalProtein == 0.0) null
        else NutritionSum(totalCal, totalProtein, totalFat, totalCarbs)
    }

    // ========== 计算配方营养（新增方法） ==========
    private suspend fun calculateRecipeNutrition(yuanliao: String): NutritionSum? {
        val ingredients = parseYuanliao(yuanliao)
        Log.d("DiaryVM", "共 ${ingredients.size} 个原料待查询")
        if (ingredients.isEmpty()) return null

        var totalCal = 0.0;
        var totalProtein = 0.0;
        var totalFat = 0.0;
        var totalCarbs = 0.0

        for ((name, grams) in ingredients) {
            try {
                // 应用别名映射
                val queryName = aliasMap[name] ?: name
                val resp = tianApiService.getNutrient(queryName)
                Log.d("DiaryVM", "$name ($queryName) 查询返回: code=${resp.code}")
                var item = resp.result?.list?.firstOrNull()
                if (item == null || item.protein == null) {
                    Log.w("DiaryVM", "$name 未找到营养数据")
                    delay(500)
                    Log.w("DiaryVM", "$name 开始AI查询营养数据")
                    item = askAiForIngredientNutrition(queryName)
                }

                val cal100g = item?.calorie?.toDoubleOrNull() ?: 0.0
                val pro100g = item?.protein?.toDoubleOrNull() ?: 0.0
                val fat100g = item?.fat?.toDoubleOrNull() ?: 0.0
                val carbs100g = item?.carbohydrate?.toDoubleOrNull() ?: 0.0

                val ratio = grams / 100.0
                totalCal += cal100g * ratio
                totalProtein += pro100g * ratio
                totalFat += fat100g * ratio
                totalCarbs += carbs100g * ratio
                delay(500)

                Log.d(
                    "DiaryVM",
                    "$name 贡献: 热量=${cal100g * ratio}, 蛋白=${pro100g * ratio}, 脂肪=${fat100g * ratio}, 碳水=${carbs100g * ratio}"
                )
                delay(500)
            } catch (e: Exception) {
                Log.e("DiaryVM", "查询原料 $name 失败", e)
            }
        }
        Log.d(
            "DiaryVM",
            "累加结果: 热量=$totalCal, 蛋白=$totalProtein, 脂肪=$totalFat, 碳水=$totalCarbs"
        )
        return if (totalCal == 0.0 && totalProtein == 0.0) null
        else NutritionSum(totalCal, totalProtein, totalFat, totalCarbs)
    }

    // ========== AI查询原料营养（新增方法） ==========
    private suspend fun askAiForIngredientNutrition(ingredientName: String): NutrientItem? {
        return try {
            val prompt = """
你是一位专业营养师。请提供每100克食材"$ingredientName"的营养成分。
请直接返回一个JSON对象，格式如下：
{
    "calorie": 数值(kcal),
    "protein": 数值(g),
    "fat": 数值(g),
    "carbohydrate": 数值(g)
}
只返回JSON，不要包含其他文字。
        """.trimIndent()

            val response = aiRepo.ask(prompt)
            Log.d("DiaryVM", "AI返回营养 ($ingredientName): $response")

            // 解析 AI 返回的 JSON
            val cal = Regex(""""calorie":\s*([\d.]+)""").find(response)?.groupValues?.get(1)
            val pro = Regex(""""protein":\s*([\d.]+)""").find(response)?.groupValues?.get(1)
            val fat = Regex(""""fat":\s*([\d.]+)""").find(response)?.groupValues?.get(1)
            val carbs = Regex(""""carbohydrate":\s*([\d.]+)""").find(response)?.groupValues?.get(1)

            if (cal != null || pro != null || fat != null || carbs != null) {
                NutrientItem(
                    name = ingredientName,
                    calorie = cal,
                    protein = pro,
                    fat = fat,
                    carbohydrate = carbs
                )
            } else null
        } catch (e: Exception) {
            Log.e("DiaryVM", "AI查询原料失败: $ingredientName", e)
            null
        }
    }

    // ========== 原料解析（新增方法） ==========
    private fun parseYuanliao(yuanliao: String): List<Pair<String, Double>> {
        val parts = yuanliao.split("[,，;；。]".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<Pair<String, Double>>()

        for (part in parts) {
            Log.d("DiaryVM", "解析原料片段: '$part'")
            // 增强版克数正则
            val gramsRegex = Regex("""(.+?)(?:\d+[只个条约]?\s*约)?(\d+(?:\.\d+)?)\s*克""")
            gramsRegex.find(part)?.let forEach@{
                var name = it.groupValues[1].trim()
                val grams = it.groupValues[2].toDoubleOrNull()
                name = name.replace(Regex("""\d+[只个条约]?\s*$"""), "").trim()
                name = name.replace(Regex("""约$"""), "").trim()
                if (name.isNotBlank() && grams != null) {
                    Log.d("DiaryVM", "  匹配到(克): $name -> ${grams}g")
                    result.add(name to grams)
                    return@forEach  // 继续下一个片段
                }
            }
            // 括号格式
            val bracketRegex =
                Regex("""(.+?)\d+[条根个只]?\s*[\(（]约?\s*(\d+(?:\.\d+)?)\s*克[\)）]""")
            bracketRegex.find(part)?.let forEach@{
                val name = it.groupValues[1].trim()
                val grams = it.groupValues[2].toDoubleOrNull()
                if (name.isNotBlank() && grams != null) {
                    Log.d("DiaryVM", "  匹配到(括号): $name -> ${grams}g")
                    result.add(name to grams)
                    return@forEach
                }
            }
            // 个/只格式
            val countRegex = Regex("""(.+?)(\d+(?:\.\d+)?)\s*[个只]""")
            countRegex.find(part)?.let {
                val name = it.groupValues[1].trim()
                val count = it.groupValues[2].toDoubleOrNull()
                if (name.isNotBlank() && count != null) {
                    Log.d("DiaryVM", "  匹配到(个): $name -> ${count * 50}g")
                    result.add(name to count * 50.0)
                }
            }
        }
        Log.d("DiaryVM", "解析完成，共提取 ${result.size} 个原料: $result")
        return result
    }

    fun clearQueryResult() {
        _queryResult.value = null
    }

    fun updateMealType(record: FoodRecordEntity, newMealType: String) {
        viewModelScope.launch {
            val updatedRecord = record.copy(mealType = newMealType)
            foodRepo.updateFoodRecord(updatedRecord)
            loadRecords()
        }
    }

    private data class NutritionSum(
        val calories: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double
    )

    // 食材别名映射（全局复用）
    private val aliasMap = mapOf(
        "葱白" to "大葱",
        "素油" to "植物油",
        "甜醋" to "醋",
        "麻油" to "芝麻油",
        // 可继续扩充
    )
}