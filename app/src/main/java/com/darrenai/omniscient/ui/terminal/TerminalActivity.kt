package com.darrenai.omniscient.ui.terminal

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.darrenai.omniscient.R
import com.darrenai.omniscient.domain.AgentLog
import com.darrenai.omniscient.ui.OmniViewModelFactory
import kotlinx.coroutines.launch

/** Monospace scrolling log showing every user message + tool call + result, live. */
class TerminalActivity : AppCompatActivity() {

    private lateinit var vm: TerminalViewModel
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        vm = ViewModelProvider(this, OmniViewModelFactory(application))[TerminalViewModel::class.java]

        logView = findViewById(R.id.terminalLog) ?: run { finish(); return }
        scroll = findViewById(R.id.terminalScroll) ?: run { finish(); return }

        findViewById<Button>(R.id.terminalClear)?.setOnClickListener { AgentLog.clear() }

        lifecycleScope.launch {
            vm.lines.collect { lines ->
                logView.text = lines.joinToString("\n")
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }
}
