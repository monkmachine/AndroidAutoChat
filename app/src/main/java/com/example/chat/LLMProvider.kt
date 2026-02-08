package com.example.chat

interface LLMProvider {
    fun chat(message: String, history: List<Map<String, String>>, callback: (String) -> Unit)
}
