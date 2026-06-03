package com.example.nutritionassistant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nutritionassistant.data.local.ReminderPreferences.Companion.BREAKFAST_MINUTE
import com.example.nutritionassistant.data.local.ReminderPreferences.Companion.DINNER_HOUR
import com.example.nutritionassistant.data.local.ReminderPreferences.Companion.DINNER_MINUTE
import com.example.nutritionassistant.data.local.ReminderPreferences.Companion.LUNCH_HOUR
import com.example.nutritionassistant.data.local.ReminderPreferences.Companion.LUNCH_MINUTE
import com.example.nutritionassistant.data.local.ReminderPreferences.Companion.REMINDER_ENABLED
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 顶层扩展属性，只在 Application 中可用
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_settings")


class ReminderPreferences(private val context: Context) {

    companion object {
        val API_KEY_WARNING_SHOWN = booleanPreferencesKey("api_key_warning_shown")
        val BREAKFAST_HOUR = intPreferencesKey("breakfast_hour")
        val BREAKFAST_MINUTE = intPreferencesKey("breakfast_minute")
        val LUNCH_HOUR = intPreferencesKey("lunch_hour")
        val LUNCH_MINUTE = intPreferencesKey("lunch_minute")
        val DINNER_HOUR = intPreferencesKey("dinner_hour")
        val DINNER_MINUTE = intPreferencesKey("dinner_minute")
        val REMINDER_ENABLED = intPreferencesKey("reminder_enabled") // 1 表示开启，0 关闭

        val TIANAPI_KEY = stringPreferencesKey("tianapi_key")
        val AIAPI_KEY = stringPreferencesKey("aiapi_key")
        val AI_REPORT_TEXT = stringPreferencesKey("ai_report_text")

    }

    // 保存提醒时间
    suspend fun saveReminderTimes(
        breakfastHour: Int, breakfastMinute: Int,
        lunchHour: Int, lunchMinute: Int,
        dinnerHour: Int, dinnerMinute: Int,
        enabled: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[BREAKFAST_HOUR] = breakfastHour
            prefs[BREAKFAST_MINUTE] = breakfastMinute
            prefs[LUNCH_HOUR] = lunchHour
            prefs[LUNCH_MINUTE] = lunchMinute
            prefs[DINNER_HOUR] = dinnerHour
            prefs[DINNER_MINUTE] = dinnerMinute
            prefs[REMINDER_ENABLED] = if (enabled) 1 else 0
        }
    }

    // 读取提醒时间
    suspend fun getReminderTimes(): ReminderTimes {
        val prefs = context.dataStore.data.first()
        return ReminderTimes(
            breakfastHour = prefs[BREAKFAST_HOUR] ?: 7,
            breakfastMinute = prefs[BREAKFAST_MINUTE] ?: 30,
            lunchHour = prefs[LUNCH_HOUR] ?: 12,
            lunchMinute = prefs[LUNCH_MINUTE] ?: 0,
            dinnerHour = prefs[DINNER_HOUR] ?: 20,
            dinnerMinute = prefs[DINNER_MINUTE] ?: 0,
            enabled = (prefs[REMINDER_ENABLED] ?: 1) == 1
        )
    }

    // 新增：保存 API Key
    suspend fun saveApiKeys(tianApiKey: String, aiApiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[TIANAPI_KEY] = tianApiKey
            prefs[AIAPI_KEY] = aiApiKey
        }
    }

    // 新增：读取 API Key
    suspend fun getApiKeys(): Pair<String, String> {
        val prefs = context.dataStore.data.first()
        val tianKey = prefs[TIANAPI_KEY] ?: ""
        val aiKey = prefs[AIAPI_KEY] ?: ""
        return Pair(tianKey, aiKey)
    }

    // 新增方法：读取是否已提示
    suspend fun isApiKeyWarningShown(): Boolean {
        return context.dataStore.data.first()[API_KEY_WARNING_SHOWN] ?: false
    }

    // 新增方法：标记已提示
    suspend fun setApiKeyWarningShown() {
        context.dataStore.edit { prefs ->
            prefs[API_KEY_WARNING_SHOWN] = true
        }
    }

    suspend fun saveAiReport(report: String) {
        context.dataStore.edit { prefs ->
            prefs[AI_REPORT_TEXT] = report
        }
    }

    // 读取 AI 报告
    suspend fun getAiReport(): String {
        return context.dataStore.data.first()[AI_REPORT_TEXT] ?: ""
    }


}

data class ReminderTimes(
    val breakfastHour: Int,
    val breakfastMinute: Int,
    val lunchHour: Int,
    val lunchMinute: Int,
    val dinnerHour: Int,
    val dinnerMinute: Int,
    val enabled: Boolean
)

