package br.com.escutamensagem

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class ScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {
    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val teal = Color.rgb(8, 126, 120)
    private var manager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var attached = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastText = ""
    private var currentPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        buildFloatingControls()
        updateVisibility(rootInActiveWindow?.packageName?.toString())
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        configureVoice()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: event?.packageName?.toString()
        if (pkg != null) updateVisibility(pkg)
    }

    override fun onInterrupt() { tts?.stop() }

    override fun onDestroy() {
        removeControls()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun buildFloatingControls() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(6), dp(7), dp(6))
            background = shape(teal, 28)
            elevation = dp(8).toFloat()
        }
        root.addView(control("🔊  OUVIR", 17) { readVisibleText() })
        root.addView(control("REPETIR", 13) { repeatLast() }.apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
        })
        root.addView(control("PARAR", 13) { tts?.stop() }.apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
        })
        panel = root
    }

    private fun control(label: String, size: Int, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size.toFloat()
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(15), dp(12), dp(15), dp(12))
        contentDescription = if (label.contains("OUVIR")) "Ouvir mensagens visíveis no WhatsApp" else "Parar leitura"
        setOnClickListener { action() }
    }

    private fun updateVisibility(packageName: String?) {
        currentPackage = packageName
        if (packageName in whatsappPackages) showControls() else removeControls()
    }

    private fun showControls() {
        if (attached) return
        val view = panel ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(28)
        }
        try {
            manager?.addView(view, params)
            attached = true
        } catch (_: Exception) { attached = false }
    }

    private fun removeControls() {
        if (!attached) return
        try { manager?.removeView(panel) } catch (_: Exception) { }
        attached = false
    }

    private fun readVisibleText() {
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() !in whatsappPackages) {
            sayToast("Abra uma conversa no WhatsApp e toque em OUVIR.")
            return
        }
        val chunks = mutableListOf<String>()
        collectText(root, chunks)
        val cleaned = chunks.map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length > 1 && !isScreenControl(it) }
            .fold(mutableListOf<String>()) { acc, item ->
                if (acc.lastOrNull() != item) acc.add(item)
                acc
            }
        val message = cleaned.joinToString(". ").take(4500)
        if (message.isBlank()) {
            sayToast("Não consegui localizar o texto. Tente deixar as mensagens visíveis na tela.")
            return
        }
        lastText = message
        if (!ttsReady) {
            sayToast("A voz ainda está carregando. Toque em OUVIR novamente.")
            return
        }
        configureVoice()
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "whatsapp-screen-read")
    }

    private fun repeatLast() {
        if (lastText.isBlank()) {
            sayToast("Toque primeiro em OUVIR.")
            return
        }
        if (!ttsReady) return
        configureVoice()
        tts?.speak(lastText, TextToSpeech.QUEUE_FLUSH, null, "whatsapp-screen-repeat")
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        if (node.text.isNullOrBlank()) node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectText(child, out) } finally { child.recycle() }
        }
    }

    private fun isScreenControl(value: String): Boolean {
        val v = value.lowercase(Locale("pt", "BR"))
        val controls = listOf("mensagem", "pesquisar", "mais opções", "anexar", "câmera", "microfone", "enviar", "voltar", "chamada de voz", "videochamada", "emoji", "digite uma mensagem", "online", "visto por último")
        return controls.any { v == it } || v.length > 1000
    }

    private fun configureVoice() {
        val engine = tts ?: return
        val pt = Locale("pt", "BR")
        engine.language = pt
        val voice = engine.voices?.filter { it.locale.language == "pt" }
            ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "BR" }
                .thenByDescending { it.quality }.thenBy { it.latency })?.firstOrNull()
        if (voice != null) engine.voice = voice
        engine.setSpeechRate(getSharedPreferences("voice_settings", MODE_PRIVATE).getFloat("speech_rate", .92f))
        engine.setPitch(1.0f)
    }

    private fun sayToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
