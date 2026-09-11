package com.darrenai.omniscient

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

/** Saved conversation list; tap to reopen, long-press to delete. */
class ConversationsActivity : AppCompatActivity() {

    private lateinit var box: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var store: ConversationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)
        store = ConversationStore(this)
        box = findViewById(R.id.convBox)
        emptyText = findViewById(R.id.emptyText)
        findViewById<Button>(R.id.newConvButton).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        box.removeAllViews()
        val list = store.list()
        emptyText.visibility = if (list.isEmpty()) TextView.VISIBLE else TextView.GONE
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        for (c in list) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.card_assistant)
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
                text = c.title.ifBlank { "Conversation" }
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
                store.delete(c.id)
                render()
                true
            }
            box.addView(card)
        }
    }
}
