package com.darrenai.omniscient.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.darrenai.omniscient.R
import com.darrenai.omniscient.ui.chat.ChatActivity
import com.darrenai.omniscient.ui.omniViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Dossier: saved sessions. Tap to reopen, long-press to shred. */
class HistoryActivity : AppCompatActivity() {

    private val vm: HistoryViewModel by omniViewModel()
    private lateinit var box: LinearLayout
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        box = findViewById(R.id.convBox)
        emptyText = findViewById(R.id.emptyText)
        findViewById<Button>(R.id.newConvButton).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        lifecycleScope.launch {
            vm.items.collect { render() }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun render() {
        val list = vm.items.value
        box.removeAllViews()
        emptyText.visibility = if (list.isEmpty()) TextView.VISIBLE else TextView.GONE
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        for (c in list) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.glass_assistant)
                setPadding(28, 24, 28, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
                isClickable = true
                isFocusable = true
                gravity = Gravity.START
            }
            val title = TextView(this).apply {
                text = c.title.ifBlank { "Session" }
                setTextColor(0xFFD6F4FF.toInt())
                textSize = 16f
            }
            val meta = TextView(this).apply {
                text = "${c.messages.size} messages · ${fmt.format(Date(c.updatedAt))}"
                setTextColor(0xFF5E7A87.toInt())
                textSize = 12f
            }
            card.addView(title)
            card.addView(meta)
            card.setOnClickListener {
                startActivity(
                    Intent(this, ChatActivity::class.java)
                        .putExtra(ChatActivity.EXTRA_ID, c.id)
                )
            }
            card.setOnLongClickListener {
                vm.delete(c.id)
                true
            }
            box.addView(card)
        }
    }
}
