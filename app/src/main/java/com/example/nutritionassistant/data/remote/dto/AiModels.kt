package com.example.nutritionassistant.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 【AI 对话请求体】
 * 发送给 阿里云通义千问 API 的数据结构
 * 作用：告诉 AI 你想问什么、用什么模型、怎么回答
 */
@JsonClass(generateAdapter = true) // Moshi 自动解析 JSON
data class ChatCompletionRequest(
    @Json(name = "model") val model: String = "qwen-turbo", // AI 模型：qwen-turbo = 快速版
    @Json(name = "messages") val messages: List<Message>,   // 对话消息列表
    @Json(name = "temperature") val temperature: Double = 0.7, // 随机性：0=严谨 1=创意
    @Json(name = "max_tokens") val maxTokens: Int = 1024    // 最大回复长度
)

/**
 * 【单条消息】
 * 一条对话内容（用户说的 / 系统提示）
 */
@JsonClass(generateAdapter = true)
data class Message(
    @Json(name = "role") val role: String,  // 角色：
    // "system" = 系统提示（设定AI性格）
    // "user"   = 用户提问
    @Json(name = "content") val content: String // 消息内容（文字）
)

/**
 * 【AI 对话响应体】
 * 通义千问 API 返回给你的完整结果
 */
@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    @Json(name = "choices") val choices: List<Choice>? // AI 返回的候选回答（通常只有1条）
)

/**
 * 【单条候选回答】
 * choices 列表里的一项
 */
@JsonClass(generateAdapter = true)
data class Choice(
    @Json(name = "message") val message: Message? // AI 回复的消息（包含 role + content）
)