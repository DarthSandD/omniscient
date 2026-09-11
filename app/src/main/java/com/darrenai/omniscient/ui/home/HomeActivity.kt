package com.darrenai.omniscient.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.darrenai.omniscient.R
import com.darrenai.omniscient.data.tools.PermissionGate
import com.darrenai.omniscient.ui.OmniViewModelFactory
import com.darrenai.omniscient.ui.chat.ChatActivity
import com.darrenai.omniscient.ui.history.HistoryActivity
import com.darrenai.omniscient.ui.onboarding.OnboardingActivity
import com.darrenai.omniscient.ui.settings.SettingsActivity
import com.darrenai.omniscient.ui.terminal.TerminalActivity
import com.darrenai.omniscient.ui.voice.VoiceManager

/** Home: status text, text input + SEND, voice, nav to other screens. */
class HomeActivity : AppCompatActivity(), VoiceManager.Callback {

    private lateinit var vm: HomeViewModel
    private lateinit var voice: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this, OmniViewModelFactory(application))[HomeViewModel::class.java]
        if (!vm.isOnboarded()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_home)

        // Voice manager is optional — crash-safe.
        voice = VoiceManager(this).apply { callback = this@HomeActivity }

        val statusText = findViewById<TextView>(R.id.homeStatus)
        val input = findViewById<EditText>(R.id.homeInput)
        val sendBtn = findViewById<Button>(R.id.homeSend)
        val voiceBtn = findViewById<ImageButton>(R.id.homeVoice)

        statusText?.text = "At your service, boss.\n${vm.getSettingsSummary()}"

        sendBtn?.setOnClickListener {
            val text = input?.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra(ChatActivity.EXTRA_INITIAL, text)
                startActivity(intent)
                input?.setText("")
            }
        }

        voiceBtn?.setOnClickListener { voice.startListening() }

        findViewById<Button>(R.id.navChat)?.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        findViewById<Button>(R.id.navHistory)?.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.navTerminal)?.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }
        findViewById<Button>(R.id.navSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onVoiceResult(text: String) {
        val input = findViewById<EditText>(R.id.homeInput)
        if (input != null && text.isNotBlank()) {
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
}
