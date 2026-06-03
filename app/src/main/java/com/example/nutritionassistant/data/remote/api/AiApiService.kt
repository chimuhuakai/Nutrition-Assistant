package com.example.nutritionassistant.data.remote.api

import com.example.nutritionassistant.data.remote.dto.ChatCompletionRequest
import com.example.nutritionassistant.data.remote.dto.ChatCompletionResponse
import com.example.nutritionassistant.data.remote.dto.RecipeResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 阿里云 通义千问 AI 对话接口
 * 作用：发送问题给 AI → 获取 AI 回答
 * 用于：营养分析、饮食建议、智能问答
 */
interface AiApiService {

    /**
     * 发送对话请求给 AI
     * 请求方式：POST
     * 接口地址：v1/chat/completions
     *
     * @param request 发送给 AI 的请求体（包含模型、消息、参数）
     * @return ChatCompletionResponse AI 返回的回答结果
     */
    @Headers("Content-Type: application/json")

    @POST("chat/completions")
    suspend fun getCompletion(
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

}