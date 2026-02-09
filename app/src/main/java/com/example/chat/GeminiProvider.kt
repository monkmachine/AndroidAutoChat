package com.example.chat

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class GeminiProvider(private val apiKey: String, private val systemPrompt: String, private val modelName: String) : LLMProvider {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

    override fun chat(message: String, history: List<Map<String, String>>, callback: (String) -> Unit) {
        // Convert history to Gemini format (contents -> parts)
        // Note: Gemini System instructions are passed differently in 1.5, 
        // but for simplicity we will just prepend it to the first user message or rely on the model.
        // Official API for system instruction is separate, let's stick to standard chat structure for now.
        
        val contentsArray = JsonArray()
        
        // 1. Add History
        history.forEach { entry ->
            val role = if (entry["role"] == "assistant") "model" else "user"
            val text = entry["content"] ?: ""
            
            val part = JsonObject()
            part.addProperty("text", text)
            val parts = JsonArray()
            parts.add(part)
            
            val content = JsonObject()
            content.addProperty("role", role)
            content.add("parts", parts)
            
            contentsArray.add(content)
        }
        
        // 2. Add Current Message (User)
        // If history is empty, prepend system prompt to this message
        val finalMessage = if (history.isEmpty()) "System: $systemPrompt\n\nUser: $message" else message
        
        val currentPart = JsonObject()
        currentPart.addProperty("text", finalMessage)
        val currentParts = JsonArray()
        currentParts.add(currentPart)
        
        val currentContent = JsonObject()
        currentContent.addProperty("role", "user")
        currentContent.add("parts", currentParts)
        
        contentsArray.add(currentContent)

        val jsonBody = JsonObject()
        jsonBody.add("contents", contentsArray)

        val request = Request.Builder()
            .url(apiUrl)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Gemini Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bodyString = response.body?.string()
                    if (response.isSuccessful && bodyString != null) {
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        val text = json.getAsJsonArray("candidates")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("content")
                            ?.getAsJsonArray("parts")
                            ?.get(0)?.asJsonObject
                            ?.get("text")?.asString ?: "No response text found"
                        callback(text)
                    } else {
                        callback("Gemini Error: ${response.code} $bodyString")
                    }
                } catch (e: Exception) {
                    callback("Gemini Parse Error: ${e.message}")
                }
            }
        })
    }
}
