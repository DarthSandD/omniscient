package com.darrenai.omniscient.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.darrenai.omniscient.R
import com.darrenai.omniscient.data.tools.PermissionGate
import com.darrenai.omniscient.domain.Message
import com.darrenai.omniscient.ui.omni
import com.darrenai.omniscient.ui.omniViewModel
import com.darrenai.omniscient.ui.terminal.TerminalActivity
import com.darrenai.omniscient.ui.voice.TtsManager
import com.darrenai.omniscient.ui.voice.VoiceManager
import kotlinx.coroutines.launch

/** Full session: glass message cards, voice in/out, live terminal shortcut. */
class ChatActivity : AppCompatActivity(), VoiceManager.Callback {

    private val vm: ChatViewModel by omniViewModel()
    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var voice: VoiceManager
    private var tts: TtsManager? = null
    private var pendingVoice = false
    private var lastSpokenSeq = 0
    private var thinking: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        voice = VoiceManager(this).apply { callback = this@ChatActivity }
        tts = runCatching { TtsManager(this) }.getOrNull()

        box = findViewById(R.id.messagesBox)
        scroll = findViewById(R.id.chatScroll)
        input = findViewById(R.id.chatInput)

        vm.open(intent.getLongExtra(EXTRA_ID, -1L))

        findViewById<Button>(R.id.chatSend).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotBlank()) {
                input.setText("")
                vm.send(this, text)
            }
        }
        findViewById<Button>(R.id.chatSpeak).setOnClickListener {
            val last = vm.lastAssistantReply()
            if (last.isNullOrBlank()) toast("Nothing to read yet, boss.")
            else tts?.speak(last) ?: toast("Voice engine unavailable.")
        }
        findViewById<Button>(R.id.chatTerminal).setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        findViewById<ImageButton>(R.id.chatMic).setOnClickListener { toggleVoice() }

        lifecycleScope.launch {
            vm.state.collect { s ->
                render(s.messages)
                if (s.busy && thinking == null) {
                    thinking = TextView(this@ChatActivity).apply {
                        text = "◇ working on it, boss…"
                        setTextColor(0xFFFFC857.toInt())
                        textSize = 13f
                        setPadding(8, 12, 8, 12)
                    }
                    box.addView(thinking)
                } else if (!s.busy && thinking != null) {
                    box.removeView(thinking)
                    thinking = null
                }
                scrollToBottom()
                if (s.replySeq > lastSpokenSeq) {
                    lastSpokenSeq = s.replySeq
                    val last = s.messages.lastOrNull { it.role == "assistant" }?.content
                    if (!last.isNullOrBlank() && omni().settings.speakReplies) tts?.speak(last)
                }
            }
        }
    }

    private fun render(messages: List<Message>) {
        box.removeAllViews()
        thinking = null
        for (m in messages) addCard(m)
    }

    private fun toggleVoice() {
        if (voice.isListening) {
            voice.stopListening()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoice = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }
        toast("Listening, boss…")
        voice.startListening()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (PermissionGate.onResult(code, results.firstOrNull() == PackageManager.PERMISSION_GRANTED)) return
        if (code == 101 && pendingVoice) {
            pendingVoice = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleVoice()
            else toast("Microphone denied, boss — you can still type.")
        }
    }

    override fun onResult(text: String) = vm.send(this, text)
    override fun onPartial(text: String) {}
    override fun onError(message: String) = toast(message)

    private fun addCard(m: Message) {
        val isUser = m.role == "user"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            gravity = if (isUser) Gravity.END else Gravity.START
        }
        val label = TextView(this).apply {
            text = if (isUser) "B O S S" else "O M N I S C I E N T"
            setTextColor(if (isUser) 0xFFFFC857.toInt() else 0xFF00E5FF.toInt())
            textSize = 10f
            letterSpacing = 0.3f
        }
        val body = TextView(this).apply {
            text = m.content
            setTextColor(0xFFD6F4FF.toInt())
            textSize = 15f
            setBackgroundResource(if (isUser) R.drawable.glass_user else R.drawable.glass_assistant)
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(label)
        row.addView(body)
        box.addView(row)
    }

    private fun scrollToBottom() {
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        voice.stopListening()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "conv_id"
    }
}
