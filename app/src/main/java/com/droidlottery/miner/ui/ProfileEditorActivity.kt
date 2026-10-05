package com.droidlottery.miner.ui

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.droidlottery.miner.R
import com.droidlottery.miner.core.config.ProfileRepository
import com.droidlottery.miner.core.config.PerformanceSettings
import com.droidlottery.miner.core.model.MiningProfile

class ProfileEditorActivity : AppCompatActivity() {
    private lateinit var repository: ProfileRepository
    private lateinit var performanceSettings: PerformanceSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_editor)
        repository = ProfileRepository(this)
        performanceSettings = PerformanceSettings(this)
        populate()
        findViewById<Button>(R.id.saveProfileButton).setOnClickListener { save() }
        findViewById<Button>(R.id.openGuideButton).setOnClickListener { startActivity(Intent(this, MiningGuideActivity::class.java)) }
        findViewById<Button>(R.id.openDonateButton).setOnClickListener { startActivity(Intent(this, DonateActivity::class.java)) }
    }

    private fun populate() = with(repository.load()) {
        findViewById<EditText>(R.id.nameInput).setText(name)
        findViewById<EditText>(R.id.hostInput).setText(host)
        findViewById<EditText>(R.id.portInput).setText(port.toString())
        findViewById<EditText>(R.id.workerInput).setText(usernameTemplate)
        findViewById<EditText>(R.id.walletInput).setText(walletAddress.orEmpty())
        findViewById<CheckBox>(R.id.tlsInput).isChecked = tls
        findViewById<EditText>(R.id.cpuThreadsInput).setText(performanceSettings.threadCount().toString())
    }

    private fun save() {
        val original = repository.load()
        val profile = original.copy(
            name = findViewById<EditText>(R.id.nameInput).text.toString().trim(),
            host = findViewById<EditText>(R.id.hostInput).text.toString().trim(),
            port = findViewById<EditText>(R.id.portInput).text.toString().toIntOrNull() ?: 0,
            usernameTemplate = findViewById<EditText>(R.id.workerInput).text.toString().trim(),
            walletAddress = findViewById<EditText>(R.id.walletInput).text.toString().trim().ifBlank { null },
            tls = findViewById<CheckBox>(R.id.tlsInput).isChecked,
        )
        profile.validationError()?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); return }
        repository.save(profile)
        val threads = findViewById<EditText>(R.id.cpuThreadsInput).text.toString().toIntOrNull()
            ?: performanceSettings.availableProcessors()
        performanceSettings.saveThreadCount(threads)
        Toast.makeText(this, "Profile saved locally", Toast.LENGTH_SHORT).show()
        finish()
    }
}
