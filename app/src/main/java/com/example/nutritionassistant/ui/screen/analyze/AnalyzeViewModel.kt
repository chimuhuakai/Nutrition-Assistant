package com.example.nutritionassistant.ui.screen.analyze
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutritionassistant.data.local.entity.FoodRecordEntity
import com.example.nutritionassistant.data.remote.api.TianApiService
import com.example.nutritionassistant.data.remote.dto.CaipinItem
import com.example.nutritionassistant.data.repository.FoodRepository
import com.example.nutritionassistant.domain.usecase.AnalyzeFoodUseCase
import com.example.nutritionassistant.domain.usecase.FullNutritionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    private val analyzeFoodUseCase: AnalyzeFoodUseCase,
    private val foodRepo: FoodRepository
) : ViewModel() {

    private val _analysisResult = MutableLiveData<FullNutritionResult?>()
    val analysisResult: LiveData<FullNutritionResult?> = _analysisResult

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    fun analyzeFood(filePath: String) {
        viewModelScope.launch {
            _loading.value = true
            val result = analyzeFoodUseCase(filePath)
            result.onSuccess { full ->
                _analysisResult.value = full
            }.onFailure {
                _analysisResult.value = null
            }
            _loading.value = false
        }
    }

    fun saveToDiary(result: FullNutritionResult) {
        viewModelScope.launch {
            val record = FoodRecordEntity(
                name = result.foodName,
                mealType = "snack",
                calories = result.calories,
                protein = result.protein,
                fat = result.fat,
                carbs = result.carbs,
                date = System.currentTimeMillis()
            )
            foodRepo.insertFoodRecord(record)
            _saveSuccess.value = true
        }
    }

    fun requeryNutrition(correctedName: String) {
        viewModelScope.launch {
            _loading.value = true
            val result = analyzeFoodUseCase.requeryWithCorrectedName(correctedName)
            _analysisResult.value = result
            _loading.value = false
        }
    }

    fun clearResult() {
        _analysisResult.value = null
    }
}