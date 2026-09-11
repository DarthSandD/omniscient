package com.darrenai.omniscient.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.darrenai.omniscient.R
import com.darrenai.omniscient.domain.Persona
import com.darrenai.omniscient.ui.home.HomeActivity
import com.darrenai.omniscient.ui.omniViewModel

/** First run: meet the butler, choose an uplink, begin service. */
class OnboardingActivity : AppCompatActivity() {

    private val vm: OnboardingViewModel by omniViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (vm.alreadyDone) {
            goHome()
            return
        }
        setContentView(R.layout.activity_onboarding)

        findViewById<TextView>(R.id.welcomeText).text = Persona.greeting() +
            "\n\nI am Omniscient — your butler in the phone. " +
            "I run your device, remember what matters, and show my work in the terminal."

        findViewById<TextView>(R.id.howText).text =
            "◆ Speak or type — both are orders.\n" +
            "◆ No key yet? I still serve: time, battery, flashlight, apps, memory.\n" +
            "◆ For full intelligence, add any OpenAI-compatible key in Uplink — " +
            "or tap the OmniRoute preset for the house backend."

        val note = findViewById<TextView>(R.id.onboardNote)
        findViewById<Button>(R.id.presetButton).setOnClickListener {
            vm.applyPreset()
            note.text = "◆ OMNIROUTE UPLINK READY, BOSS"
        }
        findViewById<Button>(R.id.beginButton).setOnClickListener {
            vm.complete()
            goHome()
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
