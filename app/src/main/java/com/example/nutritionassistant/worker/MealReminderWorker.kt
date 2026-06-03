package com.example.nutritionassistant.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nutritionassistant.R
import com.example.nutritionassistant.data.local.Database

// 三餐提醒的后台任务
// 被 NutritionAssistant 里设置的时间自动启动
class MealReminderWorker(
    context: Context,          // 上下文
    workerParams: WorkerParameters // 传参（里面有 meal_type）
) : CoroutineWorker(context, workerParams) {

    // ==============================
    // 任务真正执行的方法（自动在后台运行）
    // ==============================
    override suspend fun doWork(): Result {
        // 1. 从外面传进来的参数：拿到是早餐/午餐/晚餐
        val mealType = inputData.getString("meal_type") ?: "dinner"
        android.util.Log.d("MealWorker", "Worker 开始执行，餐别: $mealType")

        // 2. 把英文转成中文：早餐、午餐、晚餐
        val mealName = when (mealType) {
            "breakfast" -> "早餐"
            "lunch" -> "午餐"
            else -> "晚餐"
        }

        // 3. 连接本地数据库（Room）
        val appDatabase = Room.databaseBuilder(
            applicationContext,
            Database::class.java,
            "nutrition_db"
        ).build()

        // 4. 获取今天 00:00 的时间戳
        val todayStart = getTodayStart()
        // 5. 获取今天 23:59:59 的时间戳
        val todayEnd = getTodayEnd()

        // 6. 查询数据库 → 今天所有饮食记录
        val todayRecords = appDatabase.foodRecordDao().getRecordsByDate(todayStart, todayEnd)

        // 7. 判断：今天有没有记录【当前餐别】（早餐/午餐/晚餐）
        val hasMeal = todayRecords.any { it.mealType == mealType }

        // 8. 如果【没记录】→ 弹出提醒通知
        if (!hasMeal) {
            showNotification(
                "别忘了记录$mealName",
                "你今天还没记录$mealName，快去记录一下吧！"
            )
        }

        // 用完关闭数据库
        appDatabase.close()

        // 任务执行成功
        return Result.success()
    }

    // ==============================
    // 显示通知
    // ==============================
    private fun showNotification(title: String, content: String) {
        val channelId = "meal_reminder"
        val channelName = "用餐提醒"

        // Android 8.0 必须创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // 构建通知
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)        // 标题：别忘了记录早餐
            .setContentText(content)      // 内容：你还没记录...
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)          // 点击后自动消失
            .build()

        // 检查通知权限（Android 13+ 需要）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // 不同餐别用不同ID，避免通知覆盖
            val notifyId = when (inputData.getString("meal_type")) {
                "breakfast" -> 1001
                "lunch" -> 1002
                else -> 1003
            }
            // 弹出通知
            NotificationManagerCompat.from(applicationContext).notify(notifyId, notification)
        }
    }

    // ==============================
    // 获取今天 00:00:00 的时间戳
    // ==============================
    private fun getTodayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ==============================
    // 获取今天 23:59:59 的时间戳
    // ==============================
    private fun getTodayEnd(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}