package com.rocket.controller

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {

    private val API_KEY = "ar-UvJujXawcMgZKFvniORK7limUqjW6UwGTSD6MpHt"
    private val API_URL = "https://api.altrouter.ai/v1/chat/completions"
    private val MODEL = "gemini-3.1-pro"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0A0A), // Глубокий черный
                    surface = Color(0xFF1C1C1E),   // Темно-серый для элементов
                    primary = Color(0xFF007AFF)    // Яркий синий (как в iOS/DSH)
                )
            ) {
                RocketUI()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RocketUI() {
        val messages = remember { mutableStateListOf<ChatMessage>() }
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            messages.add(ChatMessage("Система ROCKET v2.0 запущена. Я готов выполнять команды. Чем займемся?", false))
        }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }

        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
            
            // ШАПКА
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                // Аватарка (Ракета)
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🚀", fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("ROCKET", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("В сети", color = Color(0xFF34C759), fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.Gray)
                }
            }

            // ЧАТ
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg -> ChatBubble(msg) }
                if (isLoading) {
                    item {
                        Text("ROCKET думает...", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // ПОЛЕ ВВОДА
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
                    .background(Color(0xFF1C1C1E), RoundedCornerShape(24.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Меню */ }) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Gray)
                }
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Напиши команду...", color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val cmd = inputText
                            inputText = ""
                            sendCommand(cmd, messages) { isLoading = it }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF007AFF))
                }
            }
        }
    }

    @Composable
    fun ChatBubble(msg: ChatMessage) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                color = if (msg.isUser) Color(0xFF007AFF) else Color(0xFF1C1C1E),
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp, 
                    bottomStart = if (msg.isUser) 16.dp else 4.dp, 
                    bottomEnd = if (msg.isUser) 4.dp else 16.dp
                )
            ) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontSize = 16.sp
                )
            }
        }
    }

       private fun sendCommand(cmd: String, messages: MutableList<ChatMessage>, setLoading: (Boolean) -> Unit) {
        messages.add(ChatMessage(cmd, true))
        
        val lowerCmd = cmd.lowercase().trim()
        
        // 1. ЛОКАЛЬНЫЙ ЗАПУСК ПРИЛОЖЕНИЙ
        val isAppLaunch = lowerCmd.startsWith("открой ") || 
                          lowerCmd.startsWith("запусти ") || 
                          (!lowerCmd.contains(" ") && lowerCmd.length > 2)

        if (isAppLaunch) {
            val appName = cmd.replace("открой", "", ignoreCase = true)
                               .replace("запусти", "", ignoreCase = true)
                               .trim()
                               
            if (launchAppByName(appName)) {
                messages.add(ChatMessage("✅ Открываю '$appName'", false))
            } else {
                messages.add(ChatMessage("❌ Не нашел '$appName'. Проверь название (например: YouTube, Telegram, VK).", false))
            }
            return
        }

        // 2. ИИ
        val service = RocketAccessibilityService.instance
        if (service == null) {
            messages.add(ChatMessage("⚠️ Служба «Доступ» выключена. Включи ROCKET в настройках.", false))
            return
        }
        
        setLoading(true)
        service.captureToBase64 { base64 ->
            if (base64 == null) {
                messages.add(ChatMessage("❌ Ошибка скриншота.", false))
                setLoading(false)
                return@captureToBase64
            }
            askAI(cmd, base64, messages, setLoading)
        }
    }

      // НОВЫЙ ПОИСК: по пакету и по частичному совпадению
    private fun launchAppByName(name: String): Boolean {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var targetPackage = ""
        val searchName = name.lowercase().trim()

        // Проходимся по всем приложениям
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val packageName = app.packageName.lowercase()

            // Ищем совпадение по названию ИЛИ по имени пакета (например, youtube)
            if (label.contains(searchName) || packageName.contains(searchName)) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    targetPackage = app.packageName
                    break
                }
            }
        }

        // Если по точному имени не нашли, ищем "умнее" (например, "ютуб" -> "youtube")
        if (targetPackage.isEmpty()) {
            val synonyms = mapOf(
                "ютуб" to "youtube",
                "телеграм" to "telegram",
                "вк" to "vk",
                "вконтакте" to "vk",
                "хром" to "chrome",
                "камера" to "camera"
            )
            val engName = synonyms[searchName] ?: searchName
            
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                val packageName = app.packageName.lowercase()
                if (label.contains(engName) || packageName.contains(engName)) {
                    if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                        targetPackage = app.packageName
                        break
                    }
                }
            }
        }

        if (targetPackage.isNotEmpty()) {
            val intent = pm.getLaunchIntentForPackage(targetPackage)
            if (intent != null) {
                startActivity(intent)
                return true
            }
        }
        return false
    }
    private fun askAI(prompt: String, base64Image: String, messages: MutableList<ChatMessage>, setLoading: (Boolean) -> Unit) {
        val bytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val realWidth = options.outWidth
        val realHeight = options.outHeight

        val systemPrompt = """
            Ты — ROCKET. Отвечай ТОЛЬКО JSON.
            1. Клик: {"action": "click", "x": 500, "y": 800}
            2. Свайп: {"action": "swipe", "x1": 500, "y1": 1500, "x2": 500, "y2": 500}
            3. Ввод: {"action": "type", "text": "привет"}
            4. Назад: {"action": "back"}
            5. Домой: {"action": "home"}
            6. Завершить: {"action": "done"}
            Размер экрана: ${realWidth}x${realHeight}.
        """.trimIndent()

        val json = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply { put("type", "text"); put("text", prompt) })
                        put(JSONObject().apply { put("type", "image_url"); put("image_url", JSONObject().apply { put("url", "data:image/jpeg;base64,$base64Image") }) })
                    })
                })
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(API_URL).addHeader("Authorization", "Bearer $API_KEY").post(body).build()

        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val resBody = response.body?.string() ?: ""
                withContext(Dispatchers.Main) {
                    try {
                        val jsonResp = JSONObject(resBody)
                        var content = jsonResp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        content = content.replace("```json", "").replace("```", "").trim()
                        messages.add(ChatMessage(content, false))
                        executeAction(content, realWidth, realHeight, messages)
                    } catch (e: Exception) {
                        messages.add(ChatMessage("❌ Ошибка парсинга: $e", false))
                    }
                    setLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    messages.add(ChatMessage("❌ Ошибка сети: ${e.message}", false))
                    setLoading(false)
                }
            }
        }
    }

    private fun executeAction(text: String, imgW: Int, imgH: Int, messages: MutableList<ChatMessage>) {
        try {
            val start = text.indexOf("{"); val end = text.lastIndexOf("}") + 1
            if (start == -1 || end == 0) return
            val actionJson = JSONObject(text.substring(start, end))
            val service = RocketAccessibilityService.instance ?: return
            val action = actionJson.getString("action")

            val dm = resources.displayMetrics
            val sx = dm.widthPixels.toFloat() / imgW.toFloat()
            val sy = dm.heightPixels.toFloat() / imgH.toFloat()

            when (action) {
                "click" -> {
                    val x = actionJson.getInt("x").toFloat() * sx
                    val y = actionJson.getInt("y").toFloat() * sy
                    service.tap(x, y, 50) { }
                }
                "swipe" -> {
                    val x1 = actionJson.getInt("x1").toFloat() * sx; val y1 = actionJson.getInt("y1").toFloat() * sy
                    val x2 = actionJson.getInt("x2").toFloat() * sx; val y2 = actionJson.getInt("y2").toFloat() * sy
                    service.swipe(x1, y1, x2, y2, 300) { }
                }
                "type" -> {
                    val txt = actionJson.getString("text")
                    service.inputText(txt) { }
                }
                "back" -> { service.globalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
                "home" -> { service.globalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
                "done" -> messages.add(ChatMessage("✅ Задача выполнена", false))
            }
        } catch (e: Exception) {
            messages.add(ChatMessage("❌ Ошибка: $e", false))
        }
    }
}
