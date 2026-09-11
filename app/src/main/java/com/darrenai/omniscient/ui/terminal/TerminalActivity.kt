package com.darrenai.omniscient.ui.terminal

import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.darrenai.omniscient.R
import com.darrenai.omniscient.ui.omniViewModel
import kotlinx.coroutines.launch

/**
 * Command terminal: every tool call the agent makes, shown live.
 * Transparency is the intelligence feel — the boss sees the work.
 */
class TerminalActivity : AppCompatActivity() {

    private val vm: TerminalViewModel by omniViewModel()
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        logView = findViewById(R.id.terminalLog)
        scroll = findViewById(R.id.terminalScroll)
        logView.typeface = Typeface.MONOSPACE

        findViewById<Button>(R.id.clearButton).setOnClickListener { vm.clear() }
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        lifecycleScope.launch {
            vm.lines.collect { lines ->
                logView.text = if (lines.isEmpty()) "— terminal clear. Give an order and watch the work, boss. —"
                else lines.joinToString("\n")
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }
}
