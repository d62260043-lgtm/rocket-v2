package com.rocket.controller

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    
    // Таймауты увеличены для Gemini
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0F0F0F),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFF4A7CFF)
                )
            ) {
                RocketUI()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RocketUI() {
        val context = LocalContext.current
        val messages = remember { mutableStateListOf<ChatMessage>() }
        var inputText by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()

        LaunchedEffect(Unit) {
            messages.add(ChatMessage("Привет, Дим! Я ROCKET. Теперь я умею не только кликать, но и управлять системой. Что делаем?", false))
        }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }

        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F)).padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Text("🤖", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("ROCKET AI", color = Color(0xFF4A7CFF), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Онлайн", color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.Gray)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg -> ChatBubble(msg) }
                if (isLoading) {
                    item {
                        Text("ROCKET печатает...", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(24.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    android.widget.Toast.makeText(context, "Меню в разработке", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Gray)
                }
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Что должен сделать телефон?", color = Color.Gray) },
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
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF4A7CFF))
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
                color = if (msg.isUser) Color(0xFF0056B3) else Color(0xFF2A2A2A),
                shape = RoundedCornerShape(16.dp, 16.dp, if (msg.isUser) 0.dp else 16.dp, if (msg.isUser) 16.dp else 0.dp)
            ) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 15.sp
                )
            }
        }
    }

    private fun sendCommand(cmd: String, messages: MutableList<ChatMessage>, setLoading: (Boolean) -> Unit) {
        val service = RocketAccessibilityService.instance
        if (service == null) {
            messages.add(ChatMessage("⚠️ Служба «Доступ» выключена. Включи ROCKET в настройках Accessibility.", false))
            return
        }
        messages.add(ChatMessage(cmd, true))
        setLoading(true)
        service.captureToBase64 { base64 ->
            if (base64 == null) {
                messages.add(ChatMessage("❌ Не удалось сделать скриншот.", false))
                setLoading(false)
                return@captureToBase64
            }
            askAI(cmd, base64, messages, setLoading)
        }
    }

    private fun askAI(prompt: String, base64Image: String, messages: MutableList<ChatMessage>, setLoading: (Boolean) -> Unit) {
        val bytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val realWidth = options.outWidth
        val realHeight = options.outHeight

        val systemPrompt = """
            Ты — ROCKET, ИИ-агент. Отвечай ТОЛЬКО валидным JSON без markdown.
            
            Если задача требует прямого системного действия (открыть приложение, будильник, фонарик), используй эти команды:
            1. Открыть приложение: {"action": "open_app", "package": "com.android.youtube"}
            2. Будильник: {"action": "alarm", "hour": 7, "minute": 0}
            3. Фонарик: {"action": "flashlight", "state": "on"} (или "off")
            4. Громкость: {"action": "volume", "level": 10} (от 0 до 15)
            5. Открыть настройки: {"action": "settings", "panel": "wifi"} (или "bluetooth")
            
            Если задача требует взаимодействия с экраном (клик, свайп), используй:
            6. Клик: {"action": "click", "x": 500, "y": 800}
            7. Свайп: {"action": "swipe", "x1": 500, "y1": 1500, "x2": 500, "y2": 500}
            8. Ввод: {"action": "type", "text": "привет"}
            9. Назад: {"action": "back"}
            10. Домой: {"action": "home"}
            11. Завершить: {"action": "done"}
            
            Размер экрана: ${realWidth}x${realHeight}.
            Внимательно смотри на скриншот. Если нужной кнопки нет — свайпай.
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
                        
                        // Очистка от markdown
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

            // --- НОВЫЕ СИСТЕМНЫЕ ДЕЙСТВИЯ ---
            if (action == "open_app") {
                val pkg = actionJson.getString("package")
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    startActivity(intent)
                    messages.add(ChatMessage("✅ Открываю $pkg", false))
                } else {
                    messages.add(ChatMessage("❌ Приложение $pkg не найдено", false))
                }
                return
            }
            
            if (action == "alarm") {
                val hour = actionJson.getInt("hour")
                val minute = actionJson.getInt("minute")
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                startActivity(intent)
                messages.add(ChatMessage("✅ Будильник на $hour:$minute", false))
                return
            }

            if (action == "flashlight") {
                val state = actionJson.getString("state") == "on"
                val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, state)
                messages.add(ChatMessage("✅ Фонарик: ${if (state) "ВКЛ" else "ВЫКЛ"}", false))
                return
            }

            if (action == "volume") {
                val level = actionJson.getInt("level")
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                messages.add(ChatMessage("✅ Громкость: $level", false))
                return
            }

            if (action == "settings") {
                val panel = actionJson.getString("panel")
                val intent = when (panel) {
                    "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                    "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    else -> Intent(Settings.ACTION_SETTINGS)
                }
                startActivity(intent)
                messages.add(ChatMessage("✅ Открываю настройки: $panel", false))
                return
            }

            // --- ДЕЙСТВИЯ С ЭКРАНОМ (КЛИКИ) ---
            val dm = resources.displayMetrics
            val sx = dm.widthPixels.toFloat() / imgW.toFloat()
            val sy = dm.heightPixels.toFloat() / imgH.toFloat()

            when (action) {
                "click" -> {
                    val origX = actionJson.getInt("x")
                    val origY = actionJson.getInt("y")
                    val x = origX.toFloat() * sx
                    val y = origY.toFloat() * sy
                    messages.add(ChatMessage("👆 Клик X:$origX Y:$origY", false))
                    service.tap(x, y, 50) { ok -> if (!ok) messages.add(ChatMessage("❌ Клик не сработал", false)) }
                }
                "swipe" -> {
                    val x1 = actionJson.getInt("x1").toFloat() * sx; val y1 = actionJson.getInt("y1").toFloat() * sy
                    val x2 = actionJson.getInt("x2").toFloat() * sx; val y2 = actionJson.getInt("y2").toFloat() * sy
                    service.swipe(x1, y1, x2, y2, 300) { ok -> if (!ok) messages.add(ChatMessage("❌ Свайп не сработал", false)) }
                }
                "type" -> {
                    val txt = actionJson.getString("text")
                    service.inputText(txt) { ok -> if (!ok) messages.add(ChatMessage("❌ Поле ввода не найдено", false)) }
                }
                "back" -> { service.globalAction(AccessibilityService.GLOBAL_ACTION_BACK); messages.add(ChatMessage("⬅️ Назад", false)) }
                "home" -> { service.globalAction(AccessibilityService.GLOBAL_ACTION_HOME); messages.add(ChatMessage("🏠 Домой", false)) }
                "done" -> messages.add(ChatMessage("✅ Задача выполнена", false))
            }
        } catch (e: Exception) {
            messages.add(ChatMessage("❌ Ошибка выполнения: $e", false))
        }
    }
}
