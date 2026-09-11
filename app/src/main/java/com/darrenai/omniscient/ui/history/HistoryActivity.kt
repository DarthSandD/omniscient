package com.darrenai.omniscient.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.darrenai.omniscient.R
import com.darrenai.omniscient.domain.Conversation
import com.darrenai.omniscient.ui.OmniViewModelFactory
import com.darrenai.omniscient.ui.chat.ChatActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** List of saved conversations (JSON files in internal storage). Tap to reopen, long-press to delete. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var vm: HistoryViewModel
    private var items: List<Conversation> = emptyList()
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private val adapter = HistoryAdapter()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        vm = ViewModelProvider(this, OmniViewModelFactory(application))[HistoryViewModel::class.java]

        list = findViewById(R.id.historyList) ?: run { finish(); return }
        empty = findViewById(R.id.historyEmpty) ?: run { finish(); return }

        list.adapter = adapter
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val item = items.getOrNull(pos) ?: return@OnItemClickListener
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, item.id)
            startActivity(intent)
        }
        list.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val item = items.getOrNull(pos) ?: return@OnItemLongClickListener false
            AlertDialog.Builder(this)
                .setTitle("Delete conversation?")
                .setMessage("\"${item.title}\"")
                .setPositiveButton("Delete") { _, _ -> vm.delete(item.id) }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        lifecycleScope.launch {
            vm.state.collect { state ->
                items = state.items
                empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = items[pos].id

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                setBackgroundColor(ContextCompat.getColor(this@HistoryActivity, R.color.glass_bg))
            }
            row.removeAllViews()
            val item = items[pos]
            row.addView(TextView(this@HistoryActivity).apply {
                text = item.title
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.cyan))
            })
            row.addView(TextView(this@HistoryActivity).apply {
                text = "${item.messages.size} msgs • ${dateFmt.format(Date(item.updatedAt))}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.text_dim))
            })
            return row
        }
    }
}
