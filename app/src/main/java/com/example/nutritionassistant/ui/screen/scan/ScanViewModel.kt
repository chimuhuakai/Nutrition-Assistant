package com.example.nutritionassistant.ui.screen.scan

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import com.example.nutritionassistant.data.remote.api.TianApiService
import com.example.nutritionassistant.data.remote.dto.NutrientItem
import com.example.nutritionassistant.data.repository.AiRepository
import com.example.nutritionassistant.data.repository.BarcodeRepository
import com.example.nutritionassistant.data.repository.FoodRepository
import com.example.nutritionassistant.domain.usecase.AnalyzeFoodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// 让 Hilt 管理这个ViewModel，自动注入仓库和API
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val barcodeRepo: BarcodeRepository,  // 条形码缓存仓库（本地数据库）
    private val foodRepo: FoodRepository,        // 食物记录仓库（保存饮食）
    private val tianApiService: TianApiService,  // 天聚数行 API 服务（查询条码、营养成分）
    private val aiRepo: AiRepository,
    private val analyzeFoodUseCase: AnalyzeFoodUseCase  //
) : ViewModel() {

    // 扫描结果（内部可修改）
    private val _scanResult = MutableLiveData<ScanResult?>()

    // 页面观察的扫描结果（只读）
    val scanResult: LiveData<ScanResult?> = _scanResult

    // 保存成功状态（内部可修改）
    private val _saveSuccess = MutableLiveData<Boolean>()

    // 页面观察的保存状态（只读）
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    private val _isScanning = MutableLiveData(true)
    val isScanning: LiveData<Boolean> = _isScanning

    /**
     * 当扫描到条形码时调用
     * 1. 先查本地缓存（快）
     * 2. 没有缓存 → 联网查询食品信息
     */
    fun onBarcodeScanned(barcode: String) {
        if (_isScanning.value == false) return
        _isScanning.value = false

        viewModelScope.launch {
            // 1. 先查本地缓存
            val cached = barcodeRepo.getBarcodeCache(barcode)
            if (cached != null) {
                _scanResult.value = ScanResult(
                    barcode = barcode,
                    foodName = cached.foodName,
                    nutritionJson = cached.nutritionJson,
                    fromCache = true,
                    calories = cached.calories,
                    protein = cached.protein,
                    fat = cached.fat,
                    carbs = cached.carbs
                )
                return@launch
            }

            // 2. 调用条形码 API
            var productName: String? = null
            try {
                val barcodeResp = tianApiService.getBarcodeInfo(barcode)
                Log.d(
                    "ScanDebug",
                    "条形码API返回: code=${barcodeResp.code}, name=${barcodeResp.result?.name}"
                )
                if (barcodeResp.code == 200 && !barcodeResp.result?.name.isNullOrBlank()) {
                    productName = barcodeResp?.result?.name ?: ""
                }
            } catch (e: Exception) {
                Log.e("ScanDebug", "条形码API调用失败", e)
            }

            // 3. 条形码 API 失败 → AI 兜底
            if (productName == null) {
                Log.d("ScanDebug", "条形码API失败，尝试AI推断...")
                productName = queryFoodNameFromAI(barcode)
            }

            // 4. 如果连 AI 也推断不出来
            if (productName == null) {
                _scanResult.value = ScanResult(barcode, "未识别出商品，请尝试拍照分析")
                return@launch
            }

            // 5. 用商品名查询完整营养（与拍照分析相同的逻辑）
            val fullNutrition = analyzeFoodUseCase.queryNutrition(
                foodName = productName,
                calories = 0f  // 扫码没有拍照识别的热量，设为0
            )

            _scanResult.value = ScanResult(
                barcode = barcode,
                foodName = productName,
                nutritionJson = null,
                fromCache = false,
                calories = fullNutrition.calories,
                protein = fullNutrition.protein,
                fat = fullNutrition.fat,
                carbs = fullNutrition.carbs
            )

// 缓存结果
            barcodeRepo.saveBarcodeCache(
                barcode = barcode,
                foodName = productName,
                nutritionJson = "",
                calories = fullNutrition.calories,
                protein = fullNutrition.protein,
                fat = fullNutrition.fat,
                carbs = fullNutrition.carbs
            )
        }
    }

    private suspend fun queryFoodNameFromAI(barcode: String): String? {
        return try {
            val prompt = """
你是一个商品条码识别专家。请根据条码 "$barcode" 推断这最可能是什么食品。
只需要返回食品名称，不要包含任何解释或其他文字。
例如：6901234567890 → "蒙牛纯牛奶"
        """.trimIndent()

            val response = aiRepo.ask(prompt).trim()
            Log.d("ScanDebug", "AI 推断商品名: $response")
            // 过滤掉可能的多余文字，只取第一行或第一个逗号前的内容
            response.lines().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("ScanDebug", "AI 推断失败", e)
            null
        }
    }

    /**
     * 保存当前扫描到的食物到饮食记录
     */
    fun saveCurrentResult(mealType: String, grams: Float) {
        val result = _scanResult.value ?: return

        viewModelScope.launch {
            val ratio = grams / 100f
            val record = FoodRecordEntity(
                name = result.foodName,
                mealType = mealType,      // 默认零食，可后续让用户选早餐/午餐/晚餐
                calories = result.calories * ratio,           // 热量（后续解析JSON填入）
                protein = result.protein * ratio,         // 蛋白质
                fat = result.fat * ratio,                // 脂肪
                carbs = result.carbs * ratio,              // 碳水
                date = System.currentTimeMillis(), // 当前时间
                barcode = result.barcode // 保存条形码
            )

            // 插入数据库
            foodRepo.insertFoodRecord(record)
            // 通知页面保存成功
            _saveSuccess.value = true
            // ← 恢复扫描
            _isScanning.value = true
            // 清空结果
            clearResult()
        }
    }

    /**
     * 清空扫描结果
     */
    fun clearResult() {
        _scanResult.value = null
    }

    fun resumeScanning() {
        _isScanning.value = true
        clearResult()
    }

    fun requeryWithCorrectedName(correctedName: String) {
        viewModelScope.launch {
            try {
                val fullNutrition = analyzeFoodUseCase.queryNutrition(correctedName, 0f)
                _scanResult.value = ScanResult(
                    barcode = _scanResult.value?.barcode ?: "",
                    foodName = correctedName,
                    nutritionJson = null,
                    fromCache = false,
                    calories = fullNutrition.calories,
                    protein = fullNutrition.protein,
                    fat = fullNutrition.fat,
                    carbs = fullNutrition.carbs
                )
            } catch (e: Exception) {
                _scanResult.value = ScanResult(
                    barcode = _scanResult.value?.barcode ?: "",
                    foodName = "查询失败: ${e.message}",
                    nutritionJson = null,
                    fromCache = false
                )
            }
        }
    }

    /**
     * 扫描结果数据类
     * 页面显示用
     */
    data class ScanResult(
        val barcode: String,
        val foodName: String,
        val nutritionJson: String? = null,
        val fromCache: Boolean = false,
        val calories: Float = 0f,
        val protein: Float = 0f,
        val fat: Float = 0f,
        val carbs: Float = 0f
    )
}