package com.ninja.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvChat: TextView
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var btnConfig: Button
    private val conversacion = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ninja_config", Context.MODE_PRIVATE)
        setContentView(buildMainLayout())
        initNinja()
    }

    private fun buildMainLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(24, 40, 24, 24)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(this).apply {
            text = "NINJA"
            textSize = 28f
            setTextColor(0xFF00FF00.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnConfig = Button(this).apply {
            text = "⚙"
            textSize = 20f
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF111111.toInt())
            setOnClickListener { mostrarConfig() }
        }
        header.addView(tvTitle)
        header.addView(btnConfig)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = 16
            }
        }
        tvChat = TextView(this).apply {
            setTextColor(0xFF00FF00.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            setPadding(8, 8, 8, 8)
        }
        scroll.addView(tvChat)
        etInput = EditText(this).apply {
            hint = "Hablá con NINJA..."
            setHintTextColor(0xFF005500.toInt())
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF111111.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
        }
        btnSend = Button(this).apply {
            text = "ENVIAR"
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00FF00.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
            setOnClickListener { enviar() }
        }
        root.addView(header)
        root.addView(scroll)
        root.addView(etInput)
        root.addView(btnSend)
        return root
    }

    private fun initNinja() {
        val systemPrompt = prefs.getString("system_prompt",
            "Sos NINJA, un asistente de IA personal. Respondé siempre en español. Sos directo, inteligente y eficiente. Sos una extensión de tu usuario.")!!
        conversacion.clear()
        conversacion.add(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
    }

    private fun enviar() {
        val mensaje = etInput.text.toString().trim()
        if (mensaje.isEmpty()) return
        agregarMensaje("Vos", mensaje)
        etInput.setText("")
        enviarAI(mensaje)
    }

    private fun agregarMensaje(quien: String, texto: String) {
        runOnUiThread { tvChat.append("\n[$quien]: $texto\n") }
    }

    private fun enviarAI(mensaje: String) {
        val provider = prefs.getString("provider", "groq")!!
        val apiKey = prefs.getString("key_$provider", "")!!
        if (apiKey.isEmpty()) {
            agregarMensaje("NINJA", "⚠️ Configurá tu API key en ⚙ primero.")
            return
        }
        conversacion.add(JSONObject().apply {
            put("role", "user")
            put("content", mensaje)
        })
        thread {
            try {
                val (endpoint, model, authHeader) = getProviderConfig(provider, apiKey)
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

    private fun getProviderConfig(provider: String, apiKey: String): Triple<String, String, String> {
        val model = prefs.getString("model_$provider", getDefaultModel(provider))!!
        return when (provider) {
            "groq" -> Triple(
                "https://api.groq.com/openai/v1/chat/completions",
                model, "Bearer $apiKey")
            "mistral" -> Triple(
                "https://api.mistral.ai/v1/chat/completions",
                model, "Bearer $apiKey")
            "gemini" -> Triple(
                "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                model, "Bearer $apiKey")
            "grok" -> Triple(
                "https://api.x.ai/v1/chat/completions",
                model, "Bearer $apiKey")
            "huggingface" -> Triple(
                "https://api-inference.huggingface.co/v1/chat/completions",
                model, "Bearer $apiKey")
            else -> Triple(
                "https://api.groq.com/openai/v1/chat/completions",
                model, "Bearer $apiKey")
        }
    }

    private fun getDefaultModel(provider: String) = when (provider) {
        "groq" -> "llama-3.3-70b-versatile"
        "mistral" -> "mistral-large-latest"
        "gemini" -> "gemini-2.0-flash"
        "grok" -> "grok-beta"
        "huggingface" -> "meta-llama/Llama-3.1-70B-Instruct"
        else -> "llama-3.3-70b-versatile"
    }

    private fun mostrarConfig() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(buildConfigLayout(dialog))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        dialog.show()
    }

    private fun buildConfigLayout(dialog: android.app.Dialog): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(24, 40, 24, 24)
        }

        fun titulo(txt: String) = TextView(this).apply {
            text = txt
            textSize = 16f
            setTextColor(0xFF00FF00.toInt())
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }

        fun campo(hint: String, key: String, password: Boolean = false): EditText {
            return EditText(this).apply {
                this.hint = hint
                setHintTextColor(0xFF005500.toInt())
                setTextColor(0xFF00FF00.toInt())
                setBackgroundColor(0xFF111111.toInt())
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 12f
                setPadding(16, 10, 16, 10)
                if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(prefs.getString(key, ""))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 4 }
            }
        }

        // PROVIDER SELECTOR
        root.addView(titulo("🧠 PROVEEDOR ACTIVO"))
        val providers = arrayOf("groq", "mistral", "gemini", "grok", "huggingface")
        val providerNames = arrayOf("Groq", "Mistral", "Gemini", "Grok (xAI)", "HuggingFace")
        val currentProvider = prefs.getString("provider", "groq")!!
        val spinnerProvider = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, providerNames)
            setSelection(providers.indexOf(currentProvider).coerceAtLeast(0))
            setBackgroundColor(0xFF111111.toInt())
        }
        root.addView(spinnerProvider)

        // API KEYS
        root.addView(titulo("🔑 API KEYS"))
        val keyGroq = campo("Groq API Key", "key_groq", true)
        val keyMistral = campo("Mistral API Key", "key_mistral", true)
        val keyGemini = campo("Gemini API Key", "key_gemini", true)
        val keyGrok = campo("Grok (xAI) API Key", "key_grok", true)
        val keyHF = campo("HuggingFace API Key", "key_huggingface", true)
        root.addView(keyGroq)
        root.addView(keyMistral)
        root.addView(keyGemini)
        root.addView(keyGrok)
        root.addView(keyHF)

        // MODELOS
        root.addView(titulo("🤖 MODELOS"))
        val modelGroq = campo("Modelo Groq (ej: llama-3.3-70b-versatile)", "model_groq")
        val modelMistral = campo("Modelo Mistral (ej: mistral-large-latest)", "model_mistral")
        val modelGemini = campo("Modelo Gemini (ej: gemini-2.0-flash)", "model_gemini")
        val modelGrok = campo("Modelo Grok (ej: grok-beta)", "model_grok")
        val modelHF = campo("Modelo HuggingFace (ej: meta-llama/Llama-3.1-70B-Instruct)", "model_huggingface")
        root.addView(modelGroq)
        root.addView(modelMistral)
        root.addView(modelGemini)
        root.addView(modelGrok)
        root.addView(modelHF)

        // SYSTEM PROMPT
        root.addView(titulo("💬 PERSONALIDAD DE NINJA"))
        val etPrompt = EditText(this).apply {
            hint = "Prompt de sistema..."
            setHintTextColor(0xFF005500.toInt())
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF111111.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setPadding(16, 10, 16, 10)
            minLines = 4
            gravity = Gravity.TOP
            setText(prefs.getString("system_prompt", "Sos NINJA, un asistente de IA personal. Respondé siempre en español. Sos directo, inteligente y eficiente."))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 4 }
        }
        root.addView(etPrompt)

        // MCP SERVERS
        root.addView(titulo("🖥️ SERVIDORES MCP"))
        val etMCP = EditText(this).apply {
            hint = "URL del servidor MCP (ej: http://localhost:3000)"
            setHintTextColor(0xFF005500.toInt())
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF111111.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setPadding(16, 10, 16, 10)
            setText(prefs.getString("mcp_server", ""))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 4 }
        }
        root.addView(etMCP)

        // HERRAMIENTAS
        root.addView(titulo("🔧 HERRAMIENTAS"))
        val toolsMap = mapOf(
            "tool_web_search" to "🔍 Búsqueda web",
            "tool_read_screen" to "👁️ Leer pantalla",
            "tool_screenshot" to "📸 Captura de pantalla",
            "tool_write_text" to "✏️ Escribir en apps"
        )
        val toolSwitches = mutableMapOf<String, Switch>()
        toolsMap.forEach { (key, label) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            val tv = TextView(this).apply {
                text = label
                setTextColor(0xFF00FF00.toInt())
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val sw = Switch(this).apply {
                isChecked = prefs.getBoolean(key, false)
                thumbTintList = android.content.res.ColorStateList.valueOf(0xFF00FF00.toInt())
            }
            toolSwitches[key] = sw
            row.addView(tv)
            row.addView(sw)
            root.addView(row)
        }

        // CONECTORES
        root.addView(titulo("🔌 CONECTORES"))
        val btnAccessibility = Button(this).apply {
            text = "⚡ Activar Accessibility Service"
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00FF00.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(btnAccessibility)

        // AGENTES
        root.addView(titulo("🤖 MODO AGENTE"))
        val rowAgente = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }
        val tvAgente = TextView(this).apply {
            text = "Modo autónomo"
            setTextColor(0xFF00FF00.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swAgente = Switch(this).apply {
            isChecked = prefs.getBoolean("agent_mode", false)
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFF00FF00.toInt())
        }
        rowAgente.addView(tvAgente)
        rowAgente.addView(swAgente)
        root.addView(rowAgente)

        // GUARDAR
        val btnGuardar = Button(this).apply {
            text = "💾 GUARDAR"
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00FF00.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 24 }
            setOnClickListener {
                prefs.edit().apply {
                    putString("provider", providers[spinnerProvider.selectedItemPosition])
                    putString("key_groq", keyGroq.text.toString())
                    putString("key_mistral", keyMistral.text.toString())
                    putString("key_gemini", keyGemini.text.toString())
                    putString("key_grok", keyGrok.text.toString())
                    putString("key_huggingface", keyHF.text.toString())
                    putString("model_groq", modelGroq.text.toString())
                    putString("model_mistral", modelMistral.text.toString())
                    putString("model_gemini", modelGemini.text.toString())
                    putString("model_grok", modelGrok.text.toString())
                    putString("model_huggingface", modelHF.text.toString())
                    putString("system_prompt", etPrompt.text.toString())
                    putString("mcp_server", etMCP.text.toString())
                    putBoolean("agent_mode", swAgente.isChecked)
                    toolSwitches.forEach { (key, sw) -> putBoolean(key, sw.isChecked) }
                    apply()
                }
                initNinja()
                dialog.dismiss()
                Toast.makeText(this@MainActivity, "✅ Configuración guardada", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnGuardar)

        val btnCerrar = Button(this).apply {
            text = "✕ CERRAR"
            setTextColor(0xFF00FF00.toInt())
            setBackgroundColor(0xFF111111.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnCerrar)
        scroll.addView(root)
        return scroll
    }
}

fun activarOverlay() {
    if (!android.provider.Settings.canDrawOverlays(this)) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName"))
        startActivity(intent)
    } else {
        startService(android.content.Intent(this, NinjaOverlayService::class.java))
    }
}
