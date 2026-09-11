package com.darrenai.omniscient.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.darrenai.omniscient.R
import com.darrenai.omniscient.ui.OmniViewModelFactory
import com.darrenai.omniscient.ui.home.HomeActivity

/** First-run welcome screen. Persists the onboarding flag and goes Home. */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var vm: OnboardingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        vm = ViewModelProvider(this, OmniViewModelFactory(application))[OnboardingViewModel::class.java]

        // If already onboarded (e.g. deep-link), just go home.
        if (vm.isOnboarded()) {
            goHome()
            return
        }

        findViewById<Button>(R.id.beginButton)?.setOnClickListener {
            vm.completeOnboarding()
            goHome()
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
