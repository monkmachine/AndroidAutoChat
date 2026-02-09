package com.example.chat

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MessagingService : Service() {

    companion object {
        const val CHANNEL_ID = "bot_channel"
        const val ACTION_REPLY = "com.example.chat.ACTION_REPLY"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val KEY_NOTIFICATION_ID = "key_notification_id"
    }
    // In-memory history map: NotificationID -> List of Messages
    private val conversationHistory = mutableMapOf<Int, MutableList<Map<String, String>>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        if (intent?.action == "com.example.chat.SIMULATE_MESSAGE") {
            // Start a NEW conversation with a unique ID
            val notificationId = System.currentTimeMillis().toInt()
            val history = mutableListOf<Map<String, String>>()
            
            val welcome = "Hello! I am ready. What would you like to talk about?"
            history.add(mapOf("role" to "assistant", "content" to welcome))
            
            conversationHistory[notificationId] = history
            sendBotMessage(notificationId)
            
        } else if (intent?.action == ACTION_REPLY) {
            val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)
            if (notificationId == -1) return START_NOT_STICKY // Should not happen

            val history = conversationHistory[notificationId] ?: return START_NOT_STICKY

            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString() ?: ""
            
            // Add User Message to History
            history.add(mapOf("role" to "user", "content" to replyText))
            
            // Update UI immediately
            sendBotMessage(notificationId)
            
            // 1. Get Provider
            val providerName = SettingsManager.getProvider(this)
            val apiKey = SettingsManager.getApiKey(this, providerName)
            val systemPrompt = SettingsManager.getSystemPrompt(this)
            
            if (apiKey.isBlank()) {
                val error = "Error: Missing API Key for $providerName."
                history.add(mapOf("role" to "assistant", "content" to error))
                sendBotMessage(notificationId)
                return START_NOT_STICKY
            }

            val llm: LLMProvider = if (providerName == "gemini") {
                val modelName = SettingsManager.getGeminiModel(this)
                GeminiProvider(apiKey, systemPrompt, modelName)
            } else {
                OpenAIProvider(apiKey, systemPrompt)
            }
            
            val historyContext = history.dropLast(1) 

            llm.chat(replyText, historyContext) { response ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    // Add Bot Response to History
                    history.add(mapOf("role" to "assistant", "content" to response))
                    sendBotMessage(notificationId)
                }
            }
        } else if (intent?.action == "IGNORE_MARK_READ") {
             val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)
             if (notificationId != -1) {
                 NotificationManagerCompat.from(this).cancel(notificationId)
                 // Optional: Clear history to free memory? 
                 // conversationHistory.remove(notificationId) 
             }
        }

        return START_NOT_STICKY
    }

    private fun sendBotMessage(notificationId: Int) {
        val history = conversationHistory[notificationId] ?: return
        if (history.isEmpty()) return
        
        // 1. Define Persona
        val me = Person.Builder().setName("Me").setKey("user_me").build()
        val bot = Person.Builder().setName("AI Bot").setKey("bot_ai").setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, android.R.drawable.ic_dialog_info)).build()

        // 2. Create Reply Action
        val replyLabel = "Reply to AI"
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build()

        val replyIntent = Intent(this, MessagingService::class.java).apply { 
            action = ACTION_REPLY 
            putExtra(KEY_NOTIFICATION_ID, notificationId)
        }
        
        val replyPendingIntent = PendingIntent.getService(
            this,
            notificationId, // Use notificationId as requestCode to keep distinct
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
         .setAllowGeneratedReplies(true)
         .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
         .setShowsUserInterface(false)
         .build()

        // 3. Create Mark as Read Action
        val markReadIntent = Intent(this, MessagingService::class.java).apply { 
            action = "IGNORE_MARK_READ"
            putExtra(KEY_NOTIFICATION_ID, notificationId)
        }
        
        val markReadPendingIntent = PendingIntent.getService(
            this,
            notificationId + 100000, // Offset to avoid conflict
            markReadIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as Read",
            markReadPendingIntent
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
         .setShowsUserInterface(false)
         .build()

        // 4. Create MessagingStyle from History
        val style = NotificationCompat.MessagingStyle(me)
            .setGroupConversation(false)
            
        history.forEach { entry ->
            val timestamp = System.currentTimeMillis() 
            val sender = if (entry["role"] == "user") null else bot 
            style.addMessage(entry["content"], timestamp, sender)
        }

        // 5. Build Notification
        val lastMessage = history.last()["content"] ?: ""
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(style)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setContentTitle("AI Bot") 
            .setContentText(lastMessage)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            
        // 6. Car Extender
        val carExtender = NotificationCompat.CarExtender()
            .setColor(resources.getColor(R.color.purple_500, theme))
            .setUnreadConversation(null) 
        
        builder.extend(carExtender)

        // 7. Check Permission
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bot Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
