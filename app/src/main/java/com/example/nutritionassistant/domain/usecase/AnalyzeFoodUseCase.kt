package com.example.nutritionassistant.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
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
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

data class FullNutritionResult(
    val foodName: String,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float
)

class AnalyzeFoodUseCase @Inject constructor(
    private val tianApiService: TianApiService,
    private val aiRepo: AiRepository
) {
    suspend operator fun invoke(imagePath: String): Result<FullNutritionResult> {
        return try {
            val base64Image = bitmapToBase64(imagePath)
            val response = tianApiService.recognizeFood(base64Image)

            if (response.code != 200 || response.result?.list.isNullOrEmpty()) {
                return Result.failure(Exception("未能识别出菜品"))
            }

            val caipin = response.result?.list?.firstOrNull()
            val foodName = caipin?.name ?: "未知菜品"
            val calories = caipin?.calorie?.toFloatOrNull() ?: 0f

            // 尝试从营养成分表获取详细营养
            val fullResult = queryNutrition(foodName, calories)

            Result.success(fullResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun queryNutrition(
        foodName: String,
        calories: Float,
    ): FullNutritionResult {
        val nutrientResp = tianApiService.getNutrient(foodName)
        Log.d(
            "AnalyzeDebug",
            "菜名=$foodName, code=${nutrientResp.code}, list size=${nutrientResp.result?.list?.size}"
        )
        val nutrientItem = nutrientResp.result?.list?.firstOrNull()

        val fullResult = if (nutrientItem != null && nutrientItem.protein != null) {
            FullNutritionResult(
                foodName = foodName,
                calories = nutrientItem.calorie?.toFloatOrNull() ?: calories,
                protein = nutrientItem.protein?.toFloatOrNull() ?: 0f,
                fat = nutrientItem.fat?.toFloatOrNull() ?: 0f,
                carbs = nutrientItem.carbohydrate?.toFloatOrNull() ?: 0f
            )
        } else {
            // 营养成分表查不到 → 用AI补全
            val aiResult = askAiForNutrition(foodName)
            aiResult ?: FullNutritionResult(foodName, calories, 0f, 0f, 0f)
        }
        return fullResult
    }

    private suspend fun askAiForNutrition(dishName: String): FullNutritionResult? {
        return try {
            val prompt = """
你是一位专业营养师。请分析菜品"$dishName"的详细营养成分。
请直接返回一个JSON对象，格式如下：
{
    "calories": 数值(kcal),
    "protein": 数值(g),
    "fat": 数值(g),
    "carbs": 数值(g)
}
只返回JSON，不要包含其他文字。
            """.trimIndent()
            val response = aiRepo.ask(prompt)
            Log.d("AnalyzeDebug", "AI返回营养: $response")
            val cal = Regex(""""calories":\s*([\d.]+)""").find(response)?.groupValues?.get(1)
                ?.toFloatOrNull() ?: 0f
            val pro = Regex(""""protein":\s*([\d.]+)""").find(response)?.groupValues?.get(1)
                ?.toFloatOrNull() ?: 0f
            val fat =
                Regex(""""fat":\s*([\d.]+)""").find(response)?.groupValues?.get(1)?.toFloatOrNull()
                    ?: 0f
            val cb = Regex(""""carbs":\s*([\d.]+)""").find(response)?.groupValues?.get(1)
                ?.toFloatOrNull() ?: 0f
            FullNutritionResult(dishName, cal, pro, fat, cb)
        } catch (e: Exception) {
            Log.e("AnalyzeDebug", "AI分析失败", e)
            null
        }
    }

    private fun bitmapToBase64(filePath: String): String {
        val bitmap = BitmapFactory.decodeStream(FileInputStream(File(filePath)))
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val baos = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val imageBytes = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            .replace("data:image/jpg;base64,", "").trim()
    }

    suspend fun requeryWithCorrectedName(foodName: String): FullNutritionResult {
        return queryNutrition(foodName, calories = 0f)
    }
}
