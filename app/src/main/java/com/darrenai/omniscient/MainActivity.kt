package com.darrenai.omniscient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** HUD home screen: reactor orb, voice button, text fallback, nav. */
class MainActivity : AppCompatActivity(), VoiceManager.Callback {

    private lateinit var orb: ReactorOrbView
    private lateinit var statusText: TextView
    private lateinit var heardText: TextView
    private lateinit var inputText: EditText
    private lateinit var prefs: Prefs
    private lateinit var store: ConversationStore
    private lateinit var repo: ChatRepository
    private lateinit var voice: VoiceManager
    private var tts: TtsManager? = null
    private var conversation: Conversation? = null
    private var pendingVoice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        store = ConversationStore(this)
        repo = ChatRepository(prefs)
        voice = VoiceManager(this).apply { callback = this@MainActivity }
        tts = runCatching { TtsManager(this) }.getOrNull()?.apply {
            onStart = { runOnUiThread { setState(OrbState.SPEAKING) } }
            onDone = { runOnUiThread { setState(OrbState.IDLE) } }
        }

        orb = findViewById(R.id.orb)
        statusText = findViewById(R.id.statusText)
        heardText = findViewById(R.id.heardText)
        inputText = findViewById(R.id.inputText)

        findViewById<ImageButton>(R.id.micButton).setOnClickListener { toggleVoice() }
        orb.setOnClickListener { toggleVoice() }
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val text = inputText.text.toString().trim()
            if (text.isNotBlank()) {
                inputText.setText("")
                ask(text)
            }
        }
        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, ConversationsActivity::class.java))
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        setState(OrbState.IDLE)
    }

    private fun setState(s: OrbState) {
        orb.state = s
        statusText.text = when (s) {
            OrbState.IDLE -> getString(R.string.status_idle)
            OrbState.LISTENING -> getString(R.string.status_listening)
            OrbState.THINKING -> getString(R.string.status_thinking)
            OrbState.SPEAKING -> getString(R.string.status_speaking)
        }
        statusText.setTextColor(
            when (s) {
                OrbState.IDLE -> 0xFF5E7A87.toInt()
                OrbState.LISTENING -> 0xFF00E5FF.toInt()
                OrbState.THINKING -> 0xFFFFC857.toInt()
                OrbState.SPEAKING -> 0xFF00E5FF.toInt()
            }
        )
    }

    private fun toggleVoice() {
        if (voice.isListening) {
            voice.stopListening()
            setState(OrbState.IDLE)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoice = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        heardText.text = ""
        setState(OrbState.LISTENING)
        voice.startListening()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 100 && pendingVoice) {
            pendingVoice = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleVoice()
            else toast("Microphone denied — you can still type.")
        }
    }

    override fun onResult(text: String) {
        heardText.text = "\u201C$text\u201D"
        ask(text)
    }

    override fun onPartial(text: String) {
        heardText.text = "\u2026$text"
    }

    override fun onError(message: String) {
        setState(OrbState.IDLE)
        toast(message)
    }

    private fun ask(prompt: String) {
        var conv = conversation
        if (conv == null) {
            conv = store.newConversation()
            conversation = conv
        }
        val c = conv
        c.messages.add(Message("user", prompt))
        setState(OrbState.THINKING)
        lifecycleScope.launch {
            when (val res = repo.complete(c.messages)) {
                is ChatResult.Ok -> {
                    c.messages.add(Message("assistant", res.text))
                    heardText.text = res.text
                    store.save(c.copy(title = ConversationStore.titleFor(c.messages)))
                    setState(OrbState.IDLE)
                    if (prefs.speakReplies) tts?.speak(res.text)
                }
                is ChatResult.Err -> {
                    c.messages.add(Message("assistant", "Error: ${res.message}"))
                    heardText.text = res.message
                    setState(OrbState.IDLE)
                }
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        voice.stopListening()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
