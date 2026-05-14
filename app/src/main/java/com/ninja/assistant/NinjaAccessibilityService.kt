package com.ninja.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class NinjaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: NinjaAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Captura eventos de pantalla
    }

    override fun onInterrupt() {}

    fun tocarPantalla(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun obtenerTextoVisible(): String {
        val root = rootInActiveWindow ?: return ""
        return extraerTexto(root)
    }

    private fun extraerTexto(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text != null) sb.append(node.text).append(" ")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { sb.append(extraerTexto(it)) }
        }
        return sb.toString()
    }

    fun escribirTexto(texto: String) {
        val root = rootInActiveWindow ?: return
        encontrarCampoYEscribir(root, texto)
    }

    private fun encontrarCampoYEscribir(node: AccessibilityNodeInfo, texto: String) {
        if (node.isEditable) {
            val args = android.os.Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { encontrarCampoYEscribir(it, texto) }
        }
    }
}
