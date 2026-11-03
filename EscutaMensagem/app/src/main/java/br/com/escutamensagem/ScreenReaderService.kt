package br.com.escutamensagem

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class ScreenReaderService : AccessibilityService(), TextToSpeech.OnInitListener {
    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val teal = Color.rgb(8, 126, 120)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideButtonRunnable = Runnable { hideListenButton() }
    private var manager: WindowManager? = null
    private var mascot: TextView? = null
    private var listenButton: TextView? = null
    private var mascotParams: WindowManager.LayoutParams? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var mascotAttached = false
    private var buttonAttached = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isSpeaking = false
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
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { setSpeaking(true) }
            override fun onDone(utteranceId: String?) { setSpeaking(false) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { setSpeaking(false) }
        })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: event?.packageName?.toString()
        if (pkg != null) updateVisibility(pkg)
    }

    override fun onInterrupt() { stopSpeaking() }

    override fun onDestroy() {
        removeControls()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun buildFloatingControls() {
        mascot = TextView(this).apply {
            text = "🤖"
            textSize = 27f
            gravity = Gravity.CENTER
            contentDescription = "Toque para mostrar Ouvir. Arraste o Androidzinho até a mensagem que quer escutar"
            background = circle(Color.rgb(31, 52, 55))
            elevation = dp(7).toFloat()
            setOnTouchListener(::moveMascot)
        }
        listenButton = TextView(this).apply {
            text = "🔊 Ouvir"
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            contentDescription = "Ouvir o texto apontado pelo Androidzinho"
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(teal, 24)
            elevation = dp(7).toFloat()
            setOnClickListener { onListenButtonTapped() }
        }

        val display = resources.displayMetrics
        val mascotSize = dp(54)
        mascotParams = overlayParams(mascotSize, mascotSize).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = display.widthPixels - mascotSize - dp(12)
            y = (display.heightPixels * .42f).toInt()
        }
        buttonParams = overlayParams(dp(104), dp(48)).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            x = dp(12)
            y = dp(178)
        }
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    private fun moveMascot(view: View, event: MotionEvent): Boolean {
        val params = mascotParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.tag = floatArrayOf(event.rawX, event.rawY, params.x.toFloat(), params.y.toFloat())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val origin = view.tag as? FloatArray ?: return true
                val display = resources.displayMetrics
                val maxX = display.widthPixels - view.width
                val maxY = display.heightPixels - view.height
                params.x = (origin[2] + event.rawX - origin[0]).toInt().coerceIn(0, maxX)
                params.y = (origin[3] + event.rawY - origin[1]).toInt().coerceIn(0, maxY)
                try { manager?.updateViewLayout(view, params) } catch (_: Exception) { }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val origin = view.tag as? FloatArray
                if (origin != null) {
                    val dx = event.rawX - origin[0]
                    val dy = event.rawY - origin[1]
                    if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) < dp(10)) revealListenButton()
                    else if (buttonAttached) scheduleButtonHide()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun updateVisibility(packageName: String?) {
        currentPackage = packageName
        if (packageName in whatsappPackages) showControls() else removeControls()
    }

    private fun showControls() {
        val wm = manager ?: return
        if (!mascotAttached) try {
            wm.addView(mascot, mascotParams)
            mascotAttached = true
        } catch (_: Exception) { mascotAttached = false }
        if (buttonVisibilityMs() == ALWAYS_VISIBLE && !buttonAttached) revealListenButton()
    }

    private fun removeControls() {
        val wm = manager ?: return
        mainHandler.removeCallbacks(hideButtonRunnable)
        if (mascotAttached) try { wm.removeView(mascot) } catch (_: Exception) { }
        if (buttonAttached) try { wm.removeView(listenButton) } catch (_: Exception) { }
        mascotAttached = false
        buttonAttached = false
    }

    private fun onListenButtonTapped() {
        if (isSpeaking) {
            stopSpeaking()
            return
        }
        mainHandler.removeCallbacks(hideButtonRunnable)
        readTextAtMascot()
        scheduleButtonHide()
    }

    private fun revealListenButton() {
        val wm = manager ?: return
        val button = listenButton ?: return
        if (!buttonAttached) try {
            wm.addView(button, buttonParams)
            buttonAttached = true
        } catch (_: Exception) { buttonAttached = false }
        if (buttonAttached) scheduleButtonHide()
    }

    private fun scheduleButtonHide() {
        mainHandler.removeCallbacks(hideButtonRunnable)
        val timeout = buttonVisibilityMs()
        if (!isSpeaking && buttonAttached && timeout > 0L) mainHandler.postDelayed(hideButtonRunnable, timeout)
    }

    private fun hideListenButton() {
        if (isSpeaking || !buttonAttached || buttonVisibilityMs() == ALWAYS_VISIBLE) return
        try { manager?.removeView(listenButton) } catch (_: Exception) { }
        buttonAttached = false
    }

    private fun readTextAtMascot() {
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() !in whatsappPackages || currentPackage !in whatsappPackages) {
            sayToast("Abra uma conversa no WhatsApp e posicione o Androidzinho sobre a mensagem.")
            return
        }
        val marker = mascot ?: return
        val location = IntArray(2)
        marker.getLocationOnScreen(location)
        val centerX = location[0] + marker.width / 2
        val centerY = location[1] + marker.height / 2
        val target = findTextAt(root, centerX, centerY)
        val selectedText = target?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (selectedText.isBlank()) {
            sayToast("Não achei texto nesse ponto. Arraste o Androidzinho para cima da mensagem.")
            return
        }
        lastText = selectedText.take(1200)
        if (!ttsReady) {
            sayToast("A voz ainda está carregando. Toque em Ouvir novamente.")
            return
        }
        configureVoice()
        tts?.speak(lastText, TextToSpeech.QUEUE_FLUSH, null, "whatsapp-selected-text")
    }

    private data class TextAtPoint(val text: String, val area: Long, val depth: Int)

    private fun findTextAt(root: AccessibilityNodeInfo, x: Int, y: Int): String? {
        val matches = mutableListOf<TextAtPoint>()
        collectTextAtPoint(root, x, y, 0, matches)
        return matches.sortedWith(compareBy<TextAtPoint> { it.area }.thenByDescending { it.depth })
            .firstOrNull()?.text
    }

    private fun collectTextAtPoint(node: AccessibilityNodeInfo, x: Int, y: Int, depth: Int, out: MutableList<TextAtPoint>) {
        if (node.isVisibleToUser) {
            val value = (node.text?.toString() ?: node.contentDescription?.toString())
                ?.replace(Regex("\\s+"), " ")?.trim()
            if (!value.isNullOrBlank() && !isScreenControl(value)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.contains(x, y)) {
                    val area = bounds.width().toLong() * bounds.height().toLong()
                    out.add(TextAtPoint(value, area, depth))
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectTextAtPoint(child, x, y, depth + 1, out) } finally { child.recycle() }
        }
    }

    private fun isScreenControl(value: String): Boolean {
        val v = value.lowercase(Locale("pt", "BR"))
        val controls = listOf("mensagem", "pesquisar", "mais opções", "anexar", "câmera", "microfone", "enviar", "voltar", "chamada de voz", "videochamada", "emoji", "digite uma mensagem", "online", "visto por último")
        return controls.any { v == it } || v.length > 1200
    }

    private fun setSpeaking(speaking: Boolean) {
        mainHandler.post {
            isSpeaking = speaking
            listenButton?.text = if (speaking) "■ Parar" else "🔊 Ouvir"
            listenButton?.contentDescription = if (speaking) "Parar leitura" else "Ouvir o texto apontado pelo Androidzinho"
            if (speaking) mainHandler.removeCallbacks(hideButtonRunnable) else scheduleButtonHide()
        }
    }

    private fun stopSpeaking() {
        tts?.stop()
        setSpeaking(false)
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
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), Color.WHITE)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buttonVisibilityMs() = getSharedPreferences("voice_settings", MODE_PRIVATE)
        .getLong("listen_button_visibility_ms", DEFAULT_BUTTON_TIMEOUT_MS)

    companion object {
        private const val DEFAULT_BUTTON_TIMEOUT_MS = 15_000L
        private const val ALWAYS_VISIBLE = 0L
    }
}
