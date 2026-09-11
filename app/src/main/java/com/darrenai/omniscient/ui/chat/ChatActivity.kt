package com.darrenai.omniscient.ui.chat

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.darrenai.omniscient.R
import com.darrenai.omniscient.data.SettingsStore
import com.darrenai.omniscient.data.tools.PermissionGate
import com.darrenai.omniscient.domain.Message
import com.darrenai.omniscient.ui.OmniViewModelFactory
import com.darrenai.omniscient.ui.voice.TtsManager
import com.darrenai.omniscient.ui.voice.VoiceManager
import kotlinx.coroutines.launch

/** Full session: message cards, voice in/out, TTS, live updates. */
class ChatActivity : AppCompatActivity(), VoiceManager.Callback {

    private lateinit var vm: ChatViewModel
    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var voice: VoiceManager
    private var tts: TtsManager? = null
    private var lastSpokenSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        vm = ViewModelProvider(this, OmniViewModelFactory(application))[ChatViewModel::class.java]

        // Voice + TTS are both optional — crash-safe.
        voice = VoiceManager(this).apply { callback = this@ChatActivity }
        tts = runCatching { TtsManager(this) }.getOrNull()

        box = findViewById(R.id.messagesBox) ?: run {
            finish(); return
        }
        scroll = findViewById(R.id.chatScroll) ?: run {
            finish(); return
        }
        input = findViewById(R.id.chatInput) ?: run {
            finish(); return
        }

        // Open existing conversation (from History) or start a new one.
        val convId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L)
        vm.open(convId)

        findViewById<Button>(R.id.chatSend)?.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotBlank()) {
                input.setText("")
                vm.send(this, text)
            }
        }
        findViewById<ImageButton>(R.id.chatVoice)?.setOnClickListener { voice.startListening() }

        // Handle initial text from Home
        val initial = intent.getStringExtra(EXTRA_INITIAL)?.trim().orEmpty()
        if (initial.isNotEmpty()) {
            vm.send(this, initial)
        }

        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            vm.state.collect { state ->
                renderMessages(state.messages)
                if (state.replySeq > lastSpokenSeq) {
                    lastSpokenSeq = state.replySeq
                    maybeSpeakLast(state.messages)
                }
            }
        }
    }

    private fun renderMessages(messages: List<Message>) {
        box.removeAllViews()
        for (msg in messages) {
            val card = TextView(this).apply {
                text = msg.content
                textSize = 15f
                setPadding(24, 16, 24, 16)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                layoutParams = params
            }
            if (msg.role == "user") {
                card.setTextColor(ContextCompat.getColor(this, R.color.cyan))
                card.setBackgroundResource(R.drawable.glass_user)
                card.gravity = Gravity.END
            } else {
                card.setTextColor(ContextCompat.getColor(this, R.color.text_light))
                card.setBackgroundResource(R.drawable.glass_assistant)
                card.gravity = Gravity.START
            }
            box.addView(card)
        }
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun maybeSpeakLast(messages: List<Message>) {
        val settings = SettingsStore(application)
        if (!settings.speakReplies) return
        val last = messages.lastOrNull { it.role == "assistant" }?.content ?: return
        tts?.speak(last)
    }

    override fun onVoiceResult(text: String) {
        if (text.isNotBlank()) {
            input.setText(text)
            input.setSelection(text.length)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (!PermissionGate.handleResult(requestCode, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INITIAL = "initial_text"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
