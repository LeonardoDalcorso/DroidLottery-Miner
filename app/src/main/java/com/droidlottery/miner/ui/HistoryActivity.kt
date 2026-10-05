package com.droidlottery.miner.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.droidlottery.miner.R
import com.droidlottery.miner.mining.stats.MiningRuntimeStore

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        val events = MiningRuntimeStore(this).readEvents()
        findViewById<TextView>(R.id.historyText).text = if (events.isEmpty()) {
            "No mining events recorded yet."
        } else {
            events.joinToString("\n\n")
        }
    }
}
