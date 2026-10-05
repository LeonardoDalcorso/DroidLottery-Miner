package com.droidlottery.miner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.droidlottery.miner.R
import com.droidlottery.miner.core.config.ProfileRepository
import com.droidlottery.miner.core.config.PerformanceSettings
import com.droidlottery.miner.mining.controller.MiningController
import com.droidlottery.miner.mining.controller.MiningStatus
import com.droidlottery.miner.mining.service.MiningForegroundService
import com.droidlottery.miner.mining.stats.MiningRuntimeStore

class MainActivity : AppCompatActivity() {
    private val controller = MiningController()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private lateinit var profileRepository: ProfileRepository
    private lateinit var runtimeStore: MiningRuntimeStore
    private val refreshRuntime = object : Runnable {
        override fun run() {
            render()
            refreshHandler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        val root = findViewById<android.view.View>(R.id.rootContent)
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(initialLeft + systemBars.left, initialTop + systemBars.top, initialRight + systemBars.right, initialBottom + systemBars.bottom)
            insets
        }
        WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, root).isAppearanceLightNavigationBars = false
        profileRepository = ProfileRepository(this)
        runtimeStore = MiningRuntimeStore(this)
        renderProfile()
        render()
        findViewById<Button>(R.id.startStopButton).setOnClickListener { toggleMining() }
        findViewById<Button>(R.id.editProfileButton).setOnClickListener {
            startActivity(Intent(this, ProfileEditorActivity::class.java))
        }
        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.consoleButton).setOnClickListener {
            startActivity(Intent(this, MiningConsoleActivity::class.java))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::profileRepository.isInitialized) renderProfile()
        refreshHandler.post(refreshRuntime)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRuntime)
        super.onPause()
    }

    private fun toggleMining() {
        if (runtimeStore.read().running) {
            controller.stop()
            startService(Intent(this, MiningForegroundService::class.java).setAction(MiningForegroundService.ACTION_STOP))
        } else {
            val battery = getSystemService(BatteryManager::class.java)
            val reportedPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val percent = reportedPercent.takeIf { it in 0..100 } ?: 100
            val charging = battery.isCharging
            controller.start(profileRepository.load(), percent, charging)
                .onSuccess {
                    ContextCompat.startForegroundService(this, Intent(this, MiningForegroundService::class.java))
                }
                .onFailure { error ->
                    Toast.makeText(this, error.message ?: "Could not start mining", Toast.LENGTH_LONG).show()
                }
        }
        render()
    }

    private fun render() {
        val snapshot = controller.snapshot
        val runtime = runtimeStore.read()
        findViewById<TextView>(R.id.statusText).text = "${runtime.status} • ${runtime.detail}"
        findViewById<TextView>(R.id.hashrateText).text = runtime.hashrate
        findViewById<HashrateChartView>(R.id.hashrateChart).submit(runtimeStore.readHashrateHistory())
        findViewById<TextView>(R.id.jobsText).text = runtime.receivedJobs.toString()
        findViewById<TextView>(R.id.acceptedText).text = runtime.acceptedShares.toString()
        findViewById<TextView>(R.id.rewardsText).text = when {
            runtime.blockCandidates > 0 -> "${runtime.blockCandidates} block candidate(s) submitted • payout pending pool confirmation"
            runtime.acceptedShares > 0 -> "${runtime.acceptedShares} accepted share(s) • payout determined by the pool"
            else -> "No payout confirmed yet"
        }
        findViewById<TextView>(R.id.thermalText).text = "Running: ${formatElapsed(runtime.startedAtMillis, runtime.running)} • Thermal: ${snapshot.thermalState} • Rejected: ${runtime.rejectedShares}"
        findViewById<Button>(R.id.startStopButton).text =
            if (runtime.running) "STOP" else "START"
    }

    private fun renderProfile() {
        with(profileRepository.load()) {
            val threads = PerformanceSettings(this@MainActivity).threadCount()
            findViewById<TextView>(R.id.profileText).text = "$name\n$coin • $algorithm • $mode\n$host:$port • $threads CPU threads"
        }
    }

    private fun formatElapsed(startedAtMillis: Long, running: Boolean): String {
        if (!running || startedAtMillis <= 0) return "00:00:00"
        val seconds = ((System.currentTimeMillis() - startedAtMillis) / 1_000).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(seconds / 3_600, seconds % 3_600 / 60, seconds % 60)
    }

    private companion object { const val NOTIFICATION_PERMISSION_REQUEST = 100 }
}
