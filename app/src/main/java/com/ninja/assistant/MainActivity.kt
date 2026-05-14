package com.ninja.assistant

import android.os.Bundle
import android.app.Activity
import android.widget.*
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var tvChat: TextView
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private var GROQ_API_KEY = ""
    private val conversacion = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvChat = findViewById(R.id.tvChat)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        conversacion.add(JSONObject().apply {
            put("role", "system")
            put("content", "Sos NINJA, un asistente de IA personal. Respondé siempre en español. Sos directo, inteligente y eficiente.")
        })
        btnSend.setOnClickListener {
            val mensaje = etInput.text.toString().trim()
            if (mensaje.isNotEmpty()) {
                agregarMensaje("Vos", mensaje)
                etInput.setText("")
                enviarAGroq(mensaje)
            }
        }
    }

    private fun agregarMensaje(quien: String, texto: String) {
        runOnUiThread {
            tvChat.append("\n[$quien]: $texto\n")
        }
    }

    private fun enviarAGroq(mensaje: String) {
        conversacion.add(JSONObject().apply {
            put("role", "user")
            put("content", mensaje)
        })
        thread {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val body = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("messages", JSONArray(conversacion))
                    put("max_tokens", 1000)
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
                agregarMensaje("NINJA", texto)
            } catch (e: Exception) {
                agregarMensaje("ERROR", e.message ?: "Error desconocido")
            }
        }
    }
}
