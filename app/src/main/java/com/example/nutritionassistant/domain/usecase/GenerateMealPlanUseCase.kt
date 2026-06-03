package com.example.nutritionassistant.domain.usecase

import com.example.nutritionassistant.data.local.entity.UserProfileEntity
import com.example.nutritionassistant.data.repository.AiRepository
import com.example.nutritionassistant.data.repository.FoodRepository
import com.example.nutritionassistant.data.repository.UserRepository
import javax.inject.Inject

/**
 * 用例：生成个性化健康餐食推荐
 * 职责：
 * 1. 获取用户资料
 * 2. 获取今日已吃的饮食记录
 * 3. 计算已摄入热量、蛋白质、脂肪、碳水
 * 4. 拼接专业营养师提示词（Prompt）
 * 5. 调用 AI → 返回个性化餐食建议
 * 属于 MVVM 中的 Domain 层（纯业务逻辑）
 */
class GenerateMealPlanUseCase @Inject constructor(
    private val userRepo: UserRepository,    // 用户资料仓库
    private val foodRepo: FoodRepository,    // 饮食记录仓库
    private val aiRepo: AiRepository         // AI 对话仓库
) {

    /**
     * 执行用例（调用方式：直接 useCase()）
     * @return Result<String> 成功返回 AI 推荐文案，失败返回异常
     */
    suspend operator fun invoke(): Result<String> {
        return try {
            // 1. 获取用户个人资料
            val profile = userRepo.getProfile()

            // 2. 获取今天所有饮食记录
            val todayRecords = foodRepo.getTodayRecords()

            // 3. 计算今日已摄入总热量、蛋白质、脂肪、碳水
            val totalCal = todayRecords.sumOf { it.calories.toDouble() }.toFloat()
            val totalProtein = todayRecords.sumOf { it.protein.toDouble() }.toFloat()
            val totalFat = todayRecords.sumOf { it.fat.toDouble() }.toFloat()
            val totalCarbs = todayRecords.sumOf { it.carbs.toDouble() }.toFloat()

            // 4. 如果用户没有填写资料 → 直接返回失败
            if (profile == null) return Result.failure(Exception("请先完善个人资料"))

            // 5. 构建 AI 营养师提示词（Prompt）
            val prompt = buildMealPrompt(profile, totalCal, totalProtein, totalFat, totalCarbs)

            // 6. 调用 AI 获取回答
            val content = aiRepo.ask(prompt)  // 直接得到字符串
            // 8. 返回成功结果
            Result.success(content)
        } catch (e: Exception) {
            // 异常处理：网络错误、AI 报错等
            Result.failure(e)
        }
    }

    /**
     * 构建给 AI 的专业营养师提示词（Prompt）
     * 把用户信息 + 今日摄入 → 拼接成自然语言指令
     */
    private fun buildMealPrompt(
        profile: UserProfileEntity,
        cal: Float, p: Float, f: Float, c: Float
    ): String {
        return """
你是一位专业营养师。根据以下用户数据，请推荐一顿适合的餐食（午餐或晚餐），并给出简短理由。

用户：${profile.name}，${profile.age}岁，${if (profile.gender == "male") "男性" else "女性"}
体重：${profile.weight}kg，身高：${profile.height}cm
目标：${when(profile.goal) { "lose_fat" -> "减脂" "gain_muscle" -> "增肌" else -> "保持体重" }}
今日已摄入：${cal.toInt()} kcal，蛋白质 ${p.toInt()}g，脂肪 ${f.toInt()}g，碳水 ${c.toInt()}g
建议每日目标约 ${(if(profile.gender=="male") 2200f else 1800f) * when(profile.goal){ "lose_fat" -> 0.8f "gain_muscle" -> 1.2f else -> 1.0f }} kcal

请直接返回推荐菜名和简短理由，不需要额外解释。
        """.trimIndent()
    }
}