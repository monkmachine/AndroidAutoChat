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
        const val NOTIFICATION_ID = 1
        const val ACTION_REPLY = "com.example.chat.ACTION_REPLY"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        if (intent?.action == "com.example.chat.SIMULATE_MESSAGE") {
            sendBotMessage("Hello! I am ready. What would you like to talk about?")
        } else if (intent?.action == ACTION_REPLY) {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString() ?: ""
            
            // 1. Get Provider
            val providerName = SettingsManager.getProvider(this)
            val apiKey = SettingsManager.getApiKey(this, providerName)
            val systemPrompt = SettingsManager.getSystemPrompt(this)
            
            if (apiKey.isBlank()) {
                sendBotMessage("Error: Missing API Key for $providerName. Please configure it in the phone app.")
                return START_NOT_STICKY
            }

            val llm: LLMProvider = if (providerName == "gemini") {
                val modelName = SettingsManager.getGeminiModel(this)
                GeminiProvider(apiKey, systemPrompt, modelName)
            } else {
                OpenAIProvider(apiKey, systemPrompt)
            }
            
            // 2. Send "Thinking..." state (Optional but good for UX)
            // sendBotMessage("Thinking...") 

            // 3. Call API
            llm.chat(replyText, emptyList()) { response ->
                // Switch to main thread if needed, but Service runs on main by default mostly, 
                // callbacks from OkHttp are background.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    sendBotMessage(response)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun sendBotMessage(text: String) {
        // 1. Define Persona
        val me = Person.Builder().setName("Me").setKey("user_me").build()
        val bot = Person.Builder().setName("AI Bot").setKey("bot_ai").setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, android.R.drawable.ic_dialog_info)).build()

        // 2. Create Reply Action
        val replyLabel = "Reply to AI"
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build()

        val replyPendingIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, MessagingService::class.java).apply { action = ACTION_REPLY },
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
        val markReadPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MessagingService::class.java).apply { action = "IGNORE_MARK_READ" },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as Read",
            markReadPendingIntent
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
         .setShowsUserInterface(false)
         .build()

        // 4. Create MessagingStyle
        // Constructor takes the user "Me"
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(text, System.currentTimeMillis(), bot)
            .setGroupConversation(false)

        // 5. Build Notification
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(style)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setContentTitle("AI Bot") // Backup for non-messaging displays
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            
        // 6. Car Extender (Minimal)
        val carExtender = NotificationCompat.CarExtender()
            .setColor(resources.getColor(R.color.purple_500, theme))
        
        builder.extend(carExtender)

        // 7. Check Init Permission again
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
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
