package com.example.chat

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.io.IOException

object GeminiModelFetcher {
    private val client = OkHttpClient()
    private val gson = Gson()

    data class ModelInfo(val name: String, val displayName: String)

    fun fetchModels(apiKey: String, callback: (List<ModelInfo>) -> Unit) {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bodyString = response.body?.string()
                    if (response.isSuccessful && bodyString != null) {
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        val models = json.getAsJsonArray("models")
                        
                        val modelList = mutableListOf<ModelInfo>()
                        
                        models.forEach { element ->
                            val obj = element.asJsonObject
                            val name = obj.get("name").asString.replace("models/", "")
                            val displayName = obj.get("displayName")?.asString ?: name
                            val supportedMethods = obj.getAsJsonArray("supportedGenerationMethods")
                            
                            // Filter for models that support text generation
                            var supportsGenerateContent = false
                            supportedMethods?.forEach { method ->
                                if (method.asString == "generateContent") {
                                    supportsGenerateContent = true
                                }
                            }

                            if (supportsGenerateContent) {
                                modelList.add(ModelInfo(name, displayName))
                            }
                        }

                        // Sort: Prioritize "flash" models, then alphabetical
                        val sortedList = modelList.sortedWith(Comparator { o1, o2 ->
                            val o1Flash = o1.name.contains("flash", ignoreCase = true)
                            val o2Flash = o2.name.contains("flash", ignoreCase = true)
                            
                            when {
                                o1Flash && !o2Flash -> -1
                                !o1Flash && o2Flash -> 1
                                else -> o1.name.compareTo(o2.name)
                            }
                        })

                        callback(sortedList)
                    } else {
                        callback(emptyList())
                    }
                } catch (e: Exception) {
                    callback(emptyList())
                }
            }
        })
    }
}
