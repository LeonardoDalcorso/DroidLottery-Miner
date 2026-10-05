package com.droidlottery.miner.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.droidlottery.miner.R
import com.droidlottery.miner.core.config.PerformanceSettings
import com.droidlottery.miner.core.config.ProfileRepository
import com.droidlottery.miner.mining.stats.MiningRuntimeStore

class MiningConsoleActivity : AppCompatActivity() {
    private lateinit var runtime: MiningRuntimeStore
    private val handler = Handler(Looper.getMainLooper())
    private val update = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_mining_console)
        val root = findViewById<android.view.View>(R.id.consoleRoot)
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(initialLeft + bars.left, initialTop + bars.top, initialRight + bars.right, initialBottom + bars.bottom)
            insets
        }
        runtime = MiningRuntimeStore(this)
    }

    override fun onResume() { super.onResume(); handler.post(update) }
    override fun onPause() { handler.removeCallbacks(update); super.onPause() }

    private fun render() {
        val state = runtime.read()
        val profile = ProfileRepository(this).load()
        findViewById<TextView>(R.id.consoleHashrate).text = state.hashrate
        findViewById<TextView>(R.id.consoleState).text = state.status
        findViewById<TextView>(R.id.consoleDetail).text = state.detail
        findViewById<TextView>(R.id.consoleJobs).text = state.receivedJobs.toString()
        findViewById<TextView>(R.id.consoleAccepted).text = state.acceptedShares.toString()
        findViewById<TextView>(R.id.consoleCandidates).text = state.blockCandidates.toString()
        findViewById<TextView>(R.id.consolePool).text = "${profile.host}:${profile.port} • ${PerformanceSettings(this).threadCount()} THREADS"
        findViewById<HashrateChartView>(R.id.consoleChart).submit(runtime.readHashrateHistory())
    }
}
