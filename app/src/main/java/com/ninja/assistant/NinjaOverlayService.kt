package com.ninja.assistant

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.speech.*
import android.speech.tts.*
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class NinjaOverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var tts: TextToSpeech
    private lateinit var prefs: SharedPreferences
    private var isListening = false
    private val conversacion = mutableListOf<JSONObject>()

    companion object {
        var instance: NinjaOverlayService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("ninja_config", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        initConversacion()
        crearOverlay()
        startForeground(1, crearNotificacion())
    }

    private fun initConversacion() {
        val prompt = prefs.getString("system_prompt",
            "Sos NINJA, un asistente de IA personal. Respondé siempre en español. Sos directo, inteligente y eficiente. Respondé de forma concisa para ser hablado en voz alta.")!!
        conversacion.clear()
        conversacion.add(JSONObject().apply {
            put("role", "system")
            put("content", prompt)
        })
    }

    private fun crearOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 120
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 16, 16, 16)
        }

        val tvEstado = TextView(this).apply {
            text = "🥷"
            textSize = 32f
            gravity = Gravity.CENTER
        }

        layout.addView(tvEstado)
        layout.setOnClickListener { toggleEscucha(tvEstado) }

        overlayView = layout
        windowManager.addView(overlayView, params)
    }

    private fun toggleEscucha(tvEstado: TextView) {
        if (isListening) return
        isListening = true
        tvEstado.text = "👂"
        escuchar(tvEstado)
    }

    private fun escuchar(tvEstado: TextView) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!texto.isNullOrEmpty()) {
                    tvEstado.text = "🤔"
                    enviarAI(texto, tvEstado)
                } else {
                    isListening = false
                    tvEstado.text = "🥷"
                }
            }
            override fun onError(error: Int) {
                isListening = false
                Handler(Looper.getMainLooper()).post { tvEstado.text = "🥷" }
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun enviarAI(mensaje: String, tvEstado: TextView) {
        val provider = prefs.getString("provider", "groq")!!
        val apiKey = prefs.getString("key_$provider", "")!!
        if (apiKey.isEmpty()) {
            hablar("Configurá tu API key primero")
            isListening = false
            Handler(Looper.getMainLooper()).post { tvEstado.text = "🥷" }
            return
}
        conversacion.add(JSONObject().apply {
            put("role", "user")
            put("content", mensaje)
        })
        thread {
            try {
                val model = prefs.getString("model_$provider", getDefaultModel(provider))!!
                val (endpoint, _, authHeader) = getProviderConfig(provider, apiKey, model)
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", authHeader)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                val body = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray(conversacion))
                    put("max_tokens", 500)
                }
                conn.outputStream.write(body.toString().toByteArray())
                val respuesta = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(respuesta)
                val texto = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                conversacion.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", texto)
                })
                Handler(Looper.getMainLooper()).post {
                    tvEstado.text = "🗣️"
                    hablar(texto)
                }
            } catch (e: Exception) {
                hablar("Error al conectar")
            } finally {
                isListening = false
                Handler(Looper.getMainLooper()).post { tvEstado.text = "🥷" }
            }
        }
    }

    private fun hablar(texto: String) {
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun getProviderConfig(provider: String, apiKey: String, model: String): Triple<String, String, String> {
        return when (provider) {
            "groq" -> Triple("https://api.groq.com/openai/v1/chat/completions", model, "Bearer $apiKey")
            "mistral" -> Triple("https://api.mistral.ai/v1/chat/completions", model, "Bearer $apiKey")
            "gemini" -> Triple("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", model, "Bearer $apiKey")
            "grok" -> Triple("https://api.x.ai/v1/chat/completions", model, "Bearer $apiKey")
            "huggingface" -> Triple("https://api-inference.huggingface.co/v1/chat/completions", model, "Bearer $apiKey")
            else -> Triple("https://api.groq.com/openai/v1/chat/completions", model, "Bearer $apiKey")
        }
    }

    private fun getDefaultModel(provider: String) = when (provider) {
        "groq" -> "llama-3.3-70b-versatile"
        "mistral" -> "mistral-large-latest"
        "gemini" -> "gemini-2.0-flash"
        "grok" -> "grok-beta"
        else -> "llama-3.3-70b-versatile"
    }

    private fun crearNotificacion(): Notification {
        val channel = NotificationChannel("ninja_channel", "NINJA", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, "ninja_channel")
            .setContentTitle("NINJA activo")
            .setContentText("Tocá el 🥷 para hablar")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = java.util.Locale("es", "AR")
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        windowManager.removeView(overlayView)
        instance = null
    }
}
