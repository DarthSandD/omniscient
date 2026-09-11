package com.darrenai.omniscient

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
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
import kotlinx.coroutines.launch

/** Full chat session with holographic message cards + voice in/out. */
class ChatActivity : AppCompatActivity(), VoiceManager.Callback {

    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var prefs: Prefs
    private lateinit var store: ConversationStore
    private lateinit var repo: ChatRepository
    private lateinit var voice: VoiceManager
    private lateinit var memory: MemoryStore
    private lateinit var tools: AgentTools
    private var tts: TtsManager? = null
    private lateinit var conv: Conversation
    private var pendingVoice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        prefs = Prefs(this)
        store = ConversationStore(this)
        repo = ChatRepository(prefs)
        memory = MemoryStore(this)
        tools = AgentTools(prefs, memory)
        voice = VoiceManager(this).apply { callback = this@ChatActivity }
        tts = runCatching { TtsManager(this) }.getOrNull()

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        conv = if (id > 0) store.load(id) ?: store.newConversation() else store.newConversation()

        box = findViewById(R.id.messagesBox)
        scroll = findViewById(R.id.chatScroll)
        input = findViewById(R.id.chatInput)

        for (m in conv.messages) addCard(m)

        findViewById<Button>(R.id.chatSend).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotBlank()) {
                input.setText("")
                send(text)
            }
        }
        findViewById<Button>(R.id.chatSpeak).setOnClickListener {
            val last = conv.messages.lastOrNull { it.role == "assistant" }?.content
            if (last.isNullOrBlank()) toast("Nothing to read yet.")
            else tts?.speak(last) ?: toast("Voice engine unavailable.")
        }
        findViewById<ImageButton>(R.id.chatMic).setOnClickListener { toggleVoice() }
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
        toast("Listening…")
        voice.startListening()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (PermissionGate.onResult(code, results.firstOrNull() == PackageManager.PERMISSION_GRANTED)) return
        if (code == 101 && pendingVoice) {
            pendingVoice = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleVoice()
            else toast("Microphone denied — you can still type.")
        }
    }

    override fun onResult(text: String) = send(text)
    override fun onPartial(text: String) {}
    override fun onError(message: String) = toast(message)

    private fun send(prompt: String) {
        val user = Message("user", prompt)
        conv.messages.add(user)
        addCard(user)
        val thinking = TextView(this).apply {
            text = "◇ thinking…"
            setTextColor(0xFFFFC857.toInt())
            textSize = 13f
            setPadding(8, 12, 8, 12)
        }
        box.addView(thinking)
        scrollToBottom()
        lifecycleScope.launch {
            // Offline intents first: work with no key and no network on any phone.
            val offline = OfflineIntents.tryHandle(this@ChatActivity, tools, prompt)
            if (offline != null) {
                box.removeView(thinking)
                val reply = Message("assistant", offline)
                conv.messages.add(reply)
                addCard(reply)
                persist()
                if (prefs.speakReplies) tts?.speak(offline)
                scrollToBottom()
                return@launch
            }
            when (val res = repo.runAgent(conv.messages, AgentTools.systemPrompt(memory.facts())) { name, args ->
                tools.run(this@ChatActivity, name, args)
            }) {
                is ChatResult.Ok -> {
                    box.removeView(thinking)
                    val reply = Message("assistant", res.text)
                    conv.messages.add(reply)
                    addCard(reply)
                    persist()
                    if (prefs.speakReplies) tts?.speak(res.text)
                }
                is ChatResult.Err -> {
                    box.removeView(thinking)
                    addCard(Message("assistant", "⚠ ${res.message}"))
                }
            }
            scrollToBottom()
        }
    }

    private fun persist() {
        conv = store.save(conv.copy(title = ConversationStore.titleFor(conv.messages)))
    }

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
            text = if (isUser) "Y O U" else "O M N I S C I E N T"
            setTextColor(if (isUser) 0xFFFFC857.toInt() else 0xFF00E5FF.toInt())
            textSize = 10f
            letterSpacing = 0.3f
        }
        val body = TextView(this).apply {
            text = m.content
            setTextColor(0xFFD6F4FF.toInt())
            textSize = 15f
            setBackgroundResource(if (isUser) R.drawable.card_user else R.drawable.card_assistant)
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        if (isUser) body.typeface = Typeface.DEFAULT
        row.addView(label)
        row.addView(body)
        box.addView(row)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onPause() {
        if (conv.messages.isNotEmpty()) persist()
        super.onPause()
    }

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
