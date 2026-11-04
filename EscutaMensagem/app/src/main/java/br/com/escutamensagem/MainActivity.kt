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
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
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
        root.addView(text("No WhatsApp, arraste o Androidzinho até a mensagem. Toque nele para mostrar “Ouvir” e escolha esse botão. Ele lê só o texto apontado.", 17, Color.rgb(77, 96, 99), false), marginTop(12))

        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.WHITE, 18)
        }
        status = text("Verificando configuração…", 18, ink, true)
        statusBox.addView(status)
        statusBox.addView(text("No WhatsApp, o robozinho fica visível. O botão Ouvir aparece conforme a duração escolhida abaixo.", 14, Color.rgb(93, 110, 112), false), marginTop(6))
        root.addView(statusBox, marginTop(24))

        root.addView(actionButton("1  ·  ATIVAR BOTÃO DE LEITURA") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, marginTop(18))
        root.addView(text("Na tela que abrir, escolha “Escuta Mensagem” e ative o serviço. Depois volte para o WhatsApp.", 14, Color.rgb(83, 100, 103), false), marginTop(8))

        root.addView(text("BOTÃO OUVIR", 13, teal, true).also { it.letterSpacing = .08f }, marginTop(24))
        root.addView(text("Toque ou arraste o robozinho para mostrar o botão. Escolha por quanto tempo ele fica na tela:", 15, ink, false), marginTop(7))
        val visibilityOptions = listOf("15 segundos", "30 segundos", "45 segundos", "1 minuto", "Sempre visível")
        val visibilityValues = listOf(15_000L, 30_000L, 45_000L, 60_000L, 0L)
        val visibilitySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, visibilityOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val savedValue = preferences().getLong("listen_button_visibility_ms", 15_000L)
            setSelection(visibilityValues.indexOf(savedValue).takeIf { it >= 0 } ?: 0)
            contentDescription = "Tempo que o botão Ouvir fica visível"
            setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    preferences().edit().putLong("listen_button_visibility_ms", visibilityValues[position]).apply()
                }
            })
        }
        root.addView(visibilitySpinner, marginTop(4))

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

        root.addView(text("ÁUDIO PERSONALIZADO", 13, teal, true).also { it.letterSpacing = .08f }, marginTop(28))
        root.addView(text("Digite uma frase e gere um arquivo de áudio com a voz do celular. O texto não é enviado. Depois, escolha a conversa no WhatsApp e toque em enviar.", 15, ink, false), marginTop(7))
        root.addView(actionButton("CRIAR ÁUDIO PARA WHATSAPP") {
            startActivity(Intent(this, CustomAudioActivity::class.java))
        }, marginTop(12))

        root.addView(text("TEXTO TEMPORÁRIO", 13, teal, true).also { it.letterSpacing = .08f }, marginTop(26))
        root.addView(text("No WhatsApp, coloque o robozinho sobre o campo de digitação e solte. O aro verde mostra que o texto foi guardado por até 1 minuto. Você pode apagar o campo e tocar em Ouvir para escutar o texto guardado. A captura acontece somente no aparelho.", 15, ink, false), marginTop(7))

        root.addView(text("Privacidade: se o robozinho estiver sobre o campo de digitação, o texto pode ficar guardado somente na memória do aparelho por até 1 minuto. Depois é apagado. O app não envia a frase escrita.", 13, Color.rgb(93, 110, 112), false), marginTop(24))
        root.addView(text("Durante a fala, o botão fica como “Parar”. Para escolher outra mensagem, arraste o robozinho.", 13, Color.rgb(93, 110, 112), false), marginTop(8))
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

class CustomAudioActivity : Activity(), TextToSpeech.OnInitListener {
    private val teal = Color.rgb(8, 126, 120)
    private var tts: TextToSpeech? = null
    private var ready = false
    private lateinit var input: EditText
    private lateinit var createButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(Color.rgb(242, 247, 246))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = "Escreva o que o robô vai falar"
            textSize = 26f
            setTextColor(Color.rgb(28, 45, 49))
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "O app transforma sua frase em um arquivo de áudio. Ele não envia a mensagem escrita. Na próxima etapa, selecione a conversa no WhatsApp e confirme o envio do áudio."
            textSize = 16f
            setTextColor(Color.rgb(77, 96, 99))
        }, spacing(10))
        input = EditText(this).apply {
            minLines = 4
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            hint = "Exemplo: Olá, tudo bem? Estou a caminho."
            textSize = 18f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.WHITE, 16)
            contentDescription = "Texto que será falado no áudio"
        }
        root.addView(input, spacing(22))
        root.addView(Button(this).apply {
            text = "TESTAR FRASE"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(teal)
            background = rounded(Color.WHITE, 14)
            setOnClickListener { speakPreview() }
        }, spacing(12))
        createButton = Button(this).apply {
            text = "GERAR E COMPARTILHAR ÁUDIO"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(teal, 14)
            setOnClickListener { createAudio() }
        }
        root.addView(createButton, spacing(12))
        root.addView(TextView(this).apply {
            text = "Dica: o WhatsApp pode apresentar o arquivo compartilhado como anexo de áudio. Você ainda escolhe a conversa e toca em enviar."
            textSize = 14f
            setTextColor(Color.rgb(93, 110, 112))
        }, spacing(22))
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        configureVoice()
        runOnUiThread { if (::createButton.isInitialized) createButton.isEnabled = ready }
    }

    private fun speakPreview() {
        val phrase = input.text.toString().trim()
        if (phrase.isBlank()) { toast("Digite uma frase primeiro."); return }
        if (!ready) { toast("A voz ainda está carregando."); return }
        configureVoice()
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "custom-preview")
    }

    private fun createAudio() {
        val phrase = input.text.toString().trim()
        if (phrase.isBlank()) { toast("Digite uma frase primeiro."); return }
        if (!ready) { toast("A voz ainda está carregando."); return }
        createButton.isEnabled = false
        configureVoice()
        val folder = File(cacheDir, "shared_audio").apply { mkdirs() }
        val output = File(folder, "mensagem-${System.currentTimeMillis()}.wav")
        val utteranceId = "custom-audio-${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    createButton.isEnabled = true
                    if (output.exists() && output.length() > 44L) shareAudio(output)
                    else toast("Não consegui criar o áudio. Tente novamente.")
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { createButton.isEnabled = true; toast("Erro ao criar o áudio.") }
            }
        })
        val result = tts?.synthesizeToFile(phrase, Bundle(), output, utteranceId) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            createButton.isEnabled = true
            toast("O mecanismo de voz não conseguiu gerar o áudio.")
        }
    }

    private fun shareAudio(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, "Áudio criado pelo Escuta Mensagem", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Enviar áudio"))
    }

    private fun configureVoice() {
        val engine = tts ?: return
        engine.language = Locale("pt", "BR")
        val best = engine.voices?.filter { it.locale.language == "pt" }
            ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "BR" }.thenByDescending { it.quality }.thenBy { it.latency })
            ?.firstOrNull()
        if (best != null) engine.voice = best
        engine.setSpeechRate(getSharedPreferences("voice_settings", Context.MODE_PRIVATE).getFloat("speech_rate", .92f))
        engine.setPitch(1.0f)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun spacing(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
