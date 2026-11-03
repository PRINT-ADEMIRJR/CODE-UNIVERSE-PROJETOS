package br.com.escutamensagem

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.speech.tts.TextToSpeech
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val teal = Color.rgb(8, 126, 120)
    private val ink = Color.rgb(28, 45, 49)
    private var status: TextView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onInit(result: Int) {
        ttsReady = result == TextToSpeech.SUCCESS
        if (ttsReady) configureVoice(tts)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(Color.rgb(242, 247, 246))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(text("ESCUTA MENSAGEM", 14, teal, true).also {
            it.letterSpacing = .08f
        })
        root.addView(text("Ouça o que está escrito no WhatsApp", 27, ink, true), marginTop(7))
        root.addView(text("No WhatsApp, arraste o Androidzinho até a mensagem que quer escutar e toque em “Ouvir”. Ele lê só o texto apontado.", 17, Color.rgb(77, 96, 99), false), marginTop(12))

        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.WHITE, 18)
        }
        status = text("Verificando configuração…", 18, ink, true)
        statusBox.addView(status)
        statusBox.addView(text("O Androidzinho e o botão pequeno aparecem somente quando o WhatsApp está aberto. Durante a fala, o botão vira “Parar”.", 14, Color.rgb(93, 110, 112), false), marginTop(6))
        root.addView(statusBox, marginTop(24))

        root.addView(actionButton("1  ·  ATIVAR BOTÃO DE LEITURA") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, marginTop(18))
        root.addView(text("Na tela que abrir, escolha “Escuta Mensagem” e ative o serviço. Depois volte para o WhatsApp.", 14, Color.rgb(83, 100, 103), false), marginTop(8))

        root.addView(text("VOZ", 13, teal, true).also { it.letterSpacing = .08f }, marginTop(26))
        root.addView(text("A naturalidade depende da voz instalada no aparelho. O app usa a voz em português do próprio Android e favorece uma fala um pouco mais calma.", 15, ink, false), marginTop(7))

        val speedLabel = text("Velocidade da fala: normal", 15, ink, true)
        root.addView(speedLabel, marginTop(15))
        val speed = SeekBar(this).apply {
            max = 60
            progress = (preferences().getFloat("speech_rate", .92f) * 100).toInt().coerceIn(70, 130) - 70
            contentDescription = "Velocidade da voz"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val rate = (progress + 70) / 100f
                    preferences().edit().putFloat("speech_rate", rate).apply()
                    speedLabel.text = "Velocidade da fala: ${if (rate < .88f) "mais devagar" else if (rate > 1.08f) "mais rápida" else "normal"}"
                }
            })
        }
        root.addView(speed)
        root.addView(actionButton("TESTAR A VOZ") { testVoice() }, marginTop(12))
        root.addView(actionButton("CONFIGURAR VOZ DO CELULAR", secondary = true) {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }, marginTop(10))

        root.addView(text("Privacidade: a leitura acontece no aparelho, somente quando o botão é tocado. O app não envia mensagens nem precisa de internet.", 13, Color.rgb(93, 110, 112), false), marginTop(24))
        root.addView(text("Para escolher outra mensagem, arraste o Androidzinho. Toque em “Parar” para interromper a fala.", 13, Color.rgb(93, 110, 112), false), marginTop(8))
    }

    private fun refreshStatus() {
        val mgr = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = mgr.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name.endsWith("ScreenReaderService") }
        status?.text = if (enabled) "✓ Leitor ativado" else "Leitor ainda não ativado"
        status?.setTextColor(if (enabled) teal else ink)
    }

    private fun testVoice() {
        if (!ttsReady) {
            android.widget.Toast.makeText(this, "A voz ainda está carregando. Tente de novo em instantes.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        speak("Olá. Este é um teste de voz. Quando você estiver no WhatsApp, toque no botão ouvir para escutar a mensagem.")
    }

    private fun speak(value: String) {
        val engine = tts ?: return
        configureVoice(engine)
        engine.speak(value, TextToSpeech.QUEUE_FLUSH, null, "escuta-teste")
    }

    private fun configureVoice(engine: TextToSpeech?) {
        if (engine == null) return
        val locale = Locale("pt", "BR")
        engine.language = locale
        val best = engine.voices
            ?.filter { it.locale.language == "pt" }
            ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "BR" }
                .thenByDescending { it.quality }
                .thenBy { it.latency })
            ?.firstOrNull()
        if (best != null) engine.voice = best
        engine.setSpeechRate(preferences().getFloat("speech_rate", .92f))
        engine.setPitch(1.0f)
    }

    private fun actionButton(label: String, secondary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        minHeight = dp(58)
        setTextColor(if (secondary) teal else Color.WHITE)
        background = rounded(if (secondary) Color.WHITE else teal, 14)
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun marginTop(px: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(px) }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun preferences() = getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
