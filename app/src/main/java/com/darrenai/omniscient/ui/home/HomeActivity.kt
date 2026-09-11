package com.darrenai.omniscient.ui.home

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
import com.darrenai.omniscient.R
import com.darrenai.omniscient.data.tools.PermissionGate
import com.darrenai.omniscient.domain.OrbState
import com.darrenai.omniscient.ui.chat.ChatActivity
import com.darrenai.omniscient.ui.history.HistoryActivity
import com.darrenai.omniscient.ui.omni
import com.darrenai.omniscient.ui.omniViewModel
import com.darrenai.omniscient.ui.onboarding.OnboardingActivity
import com.darrenai.omniscient.ui.orb.HoloOrbView
import com.darrenai.omniscient.ui.settings.SettingsActivity
import com.darrenai.omniscient.ui.terminal.TerminalActivity
import com.darrenai.omniscient.ui.voice.TtsManager
import com.darrenai.omniscient.ui.voice.VoiceManager
import kotlinx.coroutines.launch

/** Home: holographic core, voice + text orders, nav to session / terminal / history / uplink. */
class HomeActivity : AppCompatActivity(), VoiceManager.Callback {

    private val vm: HomeViewModel by omniViewModel()
    private lateinit var orb: HoloOrbView
    private lateinit var statusText: TextView
    private lateinit var heardText: TextView
    private lateinit var inputText: EditText
    private lateinit var voice: VoiceManager
    private var tts: TtsManager? = null
    private var pendingVoice = false
    private var lastSpokenSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!omni().settings.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        voice = VoiceManager(this).apply { callback = this@HomeActivity }
        tts = runCatching { TtsManager(this) }.getOrNull()?.apply {
            onStart = { runOnUiThread { vm.markSpeaking(true) } }
            onDone = { runOnUiThread { vm.markSpeaking(false) } }
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
                vm.send(this, text)
            }
        }
        findViewById<Button>(R.id.sessionButton).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        findViewById<Button>(R.id.terminalButton).setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        heardText.text = vm.greeting()
        lifecycleScope.launch {
            vm.state.collect { s ->
                orb.state = s.orb
                statusText.text = when (s.orb) {
                    OrbState.IDLE -> getString(R.string.status_idle)
                    OrbState.LISTENING -> getString(R.string.status_listening)
                    OrbState.THINKING -> getString(R.string.status_thinking)
                    OrbState.ACTING -> getString(R.string.status_acting)
                    OrbState.SPEAKING -> getString(R.string.status_speaking)
                }
                statusText.setTextColor(
                    when (s.orb) {
                        OrbState.IDLE -> 0xFF5E7A87.toInt()
                        OrbState.THINKING, OrbState.ACTING -> 0xFFFFC857.toInt()
                        else -> 0xFF00E5FF.toInt()
                    }
                )
                if (s.heard.isNotBlank()) heardText.text = s.heard
                if (s.replySeq > lastSpokenSeq) {
                    lastSpokenSeq = s.replySeq
                    if (omni().settings.speakReplies) tts?.speak(s.heard)
                }
            }
        }
    }

    private fun toggleVoice() {
        if (voice.isListening) {
            voice.stopListening()
            vm.markListening(false)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoice = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        vm.showHeard("")
        vm.markListening(true)
        voice.startListening()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (PermissionGate.onResult(code, results.firstOrNull() == PackageManager.PERMISSION_GRANTED)) return
        if (code == 100 && pendingVoice) {
            pendingVoice = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleVoice()
            else toast("Microphone denied, boss — you can still type.")
        }
    }

    override fun onResult(text: String) {
        vm.markListening(false)
        vm.send(this, text)
    }

    override fun onPartial(text: String) {
        vm.showHeard("…$text")
    }

    override fun onError(message: String) {
        vm.markListening(false)
        toast(message)
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
