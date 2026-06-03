package com.example.nutritionassistant.data.repository

import android.util.Log
import com.example.nutritionassistant.data.local.ApiKeyManager
import com.example.nutritionassistant.data.remote.api.AiApiService
import com.example.nutritionassistant.data.remote.dto.ChatCompletionRequest
import com.example.nutritionassistant.data.remote.dto.ChatCompletionResponse
import com.example.nutritionassistant.data.remote.dto.Message
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val aiApiService: AiApiService
) {
    suspend fun ask(prompt: String): String {
        val request = ChatCompletionRequest(
            model = "qwen-turbo",
            messages = listOf(Message("user", prompt)),
        )
        val response: ChatCompletionResponse = aiApiService.getCompletion(request)
        return response.choices?.firstOrNull()?.message?.content ?: ""
    }
}