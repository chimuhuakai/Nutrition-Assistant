package com.example.nutritionassistant.ui.screen.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutritionassistant.data.local.ApiKeyManager
import com.example.nutritionassistant.data.local.ReminderPreferences
import com.example.nutritionassistant.data.local.ReminderTimes
import com.example.nutritionassistant.data.local.entity.UserProfileEntity
import com.example.nutritionassistant.data.remote.api.TianApiService
import com.example.nutritionassistant.data.repository.AiRepository
import com.example.nutritionassistant.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// 让 Hilt 管理这个ViewModel，自动创建实例
@HiltViewModel
// @Inject：让Hilt自动把 UserRepository 传进来
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,  // 注入用户数据仓库（操作数据库）
    private val reminderPrefs: ReminderPreferences,  // 注入提醒偏好设置（操作本地数据库）
    private val tianApiService: TianApiService,  // 注入天行数据服务（操作网络）
    private val aiRepo: AiRepository              // 注入AI数据仓库（操作网络）
) : ViewModel() {

    private val _existingProfile = MutableLiveData<UserProfileEntity?>()
    val existingProfile: LiveData<UserProfileEntity?> = _existingProfile

    private val _reminderTimes = MutableLiveData<ReminderTimes>()
    val reminderTimes: LiveData<ReminderTimes> = _reminderTimes

    private val _tianApiKey = MutableLiveData<String>()
    val tianApiKey: LiveData<String> = _tianApiKey

    private val _aiApiKey = MutableLiveData<String>()
    val aiApiKey: LiveData<String> = _aiApiKey
    private val _testResult = MutableLiveData<String>()
    val testResult: LiveData<String> = _testResult


    val isApiKeyMissing: Boolean
        get() = ApiKeyManager.tianApiKey.isEmpty() || ApiKeyManager.aiApiKey.isEmpty()


    init {
        loadExistingProfile()
        loadReminderTimes()
        loadApiKeys()
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            _existingProfile.value = userRepo.getProfile()
        }
    }

    private fun loadReminderTimes() {
        viewModelScope.launch {
            _reminderTimes.value = reminderPrefs.getReminderTimes()
        }
    }

    /**
     * 保存用户资料
     * 接收页面传过来的：姓名、年龄、性别、体重、身高、目标
     * 然后存入数据库
     */

    fun saveProfile(
        name: String,
        age: Int,
        gender: String,
        weight: Float,
        height: Float,
        goal: String,
        blacklist: String
    ) {
        // viewModelScope：协程作用域，在后台线程执行，不卡界面
        viewModelScope.launch {
            // 调用仓库的保存方法，把数据存入Room数据库
            userRepo.saveProfile(
                UserProfileEntity(
                    name = name,
                    age = age,
                    gender = gender,
                    weight = weight,
                    height = height,
                    goal = goal,
                    blacklist = blacklist
                )
            )
        }
    }

    fun saveReminderTimes(
        breakfastHour: Int, breakfastMinute: Int,
        lunchHour: Int, lunchMinute: Int,
        dinnerHour: Int, dinnerMinute: Int,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            reminderPrefs.saveReminderTimes(
                breakfastHour, breakfastMinute,
                lunchHour, lunchMinute,
                dinnerHour, dinnerMinute,
                enabled
            )
        }
    }
    private fun loadApiKeys() {
        viewModelScope.launch {
            val (tianKey, aiKey) = reminderPrefs.getApiKeys()
            _tianApiKey.value = tianKey
            _aiApiKey.value = aiKey
        }
    }

    fun saveApiKeys(tianKey: String, aiKey: String) {
        viewModelScope.launch {
            persistApiKey(tianKey, aiKey)
        }
    }

    private suspend fun persistApiKey(
        tianKey: String,
        aiKey: String
    ) {
        // 更新缓存
        if (tianKey.isNotEmpty()) ApiKeyManager.tianApiKey = tianKey
        if (aiKey.isNotEmpty()) ApiKeyManager.aiApiKey = aiKey

        // 持久化写入，使用 NonCancellable 确保不被取消
        withContext(NonCancellable) {
            reminderPrefs.saveApiKeys(tianKey, aiKey)
        }

        // 持久化
        _tianApiKey.value = tianKey
        _aiApiKey.value = aiKey
    }

    fun testApiConnection(tianKey: String, aiKey: String) {

        viewModelScope.launch {
            persistApiKey(tianKey, aiKey)

            val results = mutableListOf<String>()

            // 临时替换 Key（避免影响全局）
            val oldTianKey = ApiKeyManager.tianApiKey
            val oldAiKey = ApiKeyManager.aiApiKey
            ApiKeyManager.tianApiKey = tianKey
            ApiKeyManager.aiApiKey = aiKey

            Log.d("ApiTest", "使用的 AI Key 前8位: ${aiKey.take(8)}, 长度: ${aiKey.length}")

            try {
                // 测试天聚数行
                try {
                    val resp = tianApiService.getNutrient("苹果")
                    if (resp.code == 200) {
                        results.add("✅ 天聚数行连接成功")
                    } else {
                        results.add("❌ 天聚数行返回错误: ${resp.msg}")
                    }
                } catch (e: Exception) {
                    results.add("❌ 天聚数行连接失败: ${e.message}")
                }

                // 测试通义千问
                try {
                    val aiResp = aiRepo.ask("请回复OK，不要包含其他文字。")
                    if (aiResp.contains("OK", ignoreCase = true)) {
                        results.add("✅ 通义千问连接成功")
                    } else {
                        results.add("⚠️ 通义千问返回未知内容: $aiResp")
                    }
                } catch (e: Exception) {
                    results.add("❌ 通义千问连接失败: ${e.message}")
                }
            } finally {
                // 恢复原来的 Key
                ApiKeyManager.tianApiKey = oldTianKey
                ApiKeyManager.aiApiKey = oldAiKey
            }

            _testResult.value = results.joinToString("\n")
        }
    }

}