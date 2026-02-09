package com.example.chat

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OpenAIProvider(private val apiKey: String, private val systemPrompt: String) : LLMProvider {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiUrl = "https://api.openai.com/v1/chat/completions"

    override fun chat(message: String, history: List<Map<String, String>>, callback: (String) -> Unit) {
        val jsonBody = JsonObject().apply {
            addProperty("model", "gpt-3.5-turbo")
            // Reconstruct messages array
            val messagesList = mutableListOf<Map<String, String>>()
            messagesList.add(mapOf("role" to "system", "content" to systemPrompt))
            
            // Add history
            messagesList.addAll(history)
            
            // Add current message
            messagesList.add(mapOf("role" to "user", "content" to message))
            
            add("messages", gson.toJsonTree(messagesList))
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("OpenAI Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bodyString = response.body?.string()
                    if (response.isSuccessful && bodyString != null) {
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        val text = json.getAsJsonArray("choices")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("message")
                            ?.get("content")?.asString ?: "No content"
                        callback(text)
                    } else {
                        callback("OpenAI Error: ${response.code} ${response.message}")
                    }
                } catch (e: Exception) {
                    callback("OpenAI Parse Error: ${e.message}")
                }
            }
        })
    }
}
