// 应用包名
package com.example.nutritionassistant

// 导入必要的类：Application、WorkManager、约束、任务、时间单位
import android.app.Application
import androidx.work.*
import com.example.nutritionassistant.data.local.ApiKeyManager
import com.example.nutritionassistant.data.local.ReminderPreferences
import com.example.nutritionassistant.data.local.ReminderTimes
import com.example.nutritionassistant.worker.MealReminderWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Hilt 依赖注入注解（让整个App支持自动注入）
@HiltAndroidApp
// 应用入口类，APP一启动就运行这里
class NutritionAssistant : Application() {

    @Inject
    lateinit var reminderPrefs: ReminderPreferences

    // APP 创建时执行
    override fun onCreate() {
        super.onCreate()

        kotlinx.coroutines.MainScope().launch {
            // 加载 API Key 到缓存
            val (tian, ai) = reminderPrefs.getApiKeys()
            ApiKeyManager.tianApiKey = tian
            ApiKeyManager.aiApiKey = ai
            // 启动：安排早、午、晚三餐提醒
            val times = reminderPrefs.getReminderTimes()
            if (times.enabled) {
                scheduleMealReminders(times)
            }
        }
    }


    // =========================================
    // 安排三餐提醒
    // =========================================
    private fun scheduleMealReminders(times: ReminderTimes) {
        // 设置任务约束条件：不需要网络就能运行
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        scheduleMealReminder(
            "breakfast_reminder",
            times.breakfastHour,
            times.breakfastMinute,
            "breakfast",
            constraints
        )
        scheduleMealReminder(
            "lunch_reminder",
            times.lunchHour,
            times.lunchMinute,
            "lunch",
            constraints
        )
        scheduleMealReminder(
            "dinner_reminder",
            times.dinnerHour,
            times.dinnerMinute,
            "dinner",
            constraints
        )
    }

    // =========================================
    // 通用方法：创建一个定时提醒任务
    // =========================================
   public fun scheduleMealReminder(
        tag: String,          // 任务唯一标识（如 breakfast_reminder）
        hour: Int,            // 几点
        minute: Int,          // 几分
        mealType: String,     // 餐别：breakfast / lunch / dinner
        constraints: Constraints  // 运行条件
    ) {
        // 把“餐别”数据传给 MealReminderWorker
        // 让Worker知道现在是提醒早餐、午餐还是晚餐
        val inputData = Data.Builder()
            .putString("meal_type", mealType)
            .build()

        // 创建【每天重复】的后台任务
        val reminderRequest = PeriodicWorkRequestBuilder<MealReminderWorker>(
            1, TimeUnit.DAYS  // 周期：每1天执行一次
        )
            .setConstraints(constraints)       // 设置条件：无需网络
            .setInitialDelay(                 // 第一次执行延迟多久（到今天的目标时间）
                calculateInitialDelay(hour, minute),
                TimeUnit.MILLISECONDS
            )
            .setInputData(inputData)          // 把餐别传给Worker
            .build()

        // 把任务交给系统执行
        // KEEP：如果任务已存在，就保留，不重复创建
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            tag,
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )
    }

    /**
     * 重新调度提醒任务（用户修改时间后调用）
     */
    fun rescheduleMealReminders(times: ReminderTimes) {
        kotlinx.coroutines.MainScope().launch {
            if (times.enabled) {
                scheduleMealReminders(times)
            } else {
                cancelAllMealReminders()
            }
        }
    }

    private fun cancelAllMealReminders() {
        WorkManager.getInstance(this).cancelAllWorkByTag("breakfast_reminder")
        WorkManager.getInstance(this).cancelAllWorkByTag("lunch_reminder")
        WorkManager.getInstance(this).cancelAllWorkByTag("dinner_reminder")
    }


    // =========================================
    // 计算：从现在到【今天目标时间】还要等多久
    // =========================================
    private fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = java.util.Calendar.getInstance()  // 当前时间

        // 设置目标时间：今天的 hour:minute:00
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        // 如果当前时间已经过了目标时间 → 目标改为【明天】同一时间
        if (now.after(target)) {
            target.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        // 返回：还差多少毫秒到达目标时间
        return target.timeInMillis - now.timeInMillis
    }
}

