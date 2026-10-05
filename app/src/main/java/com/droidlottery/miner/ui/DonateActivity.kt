package com.droidlottery.miner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.droidlottery.miner.R

class DonateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)
        findViewById<Button>(R.id.copyDonateAddressButton).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("BTC donation address", BTC_ADDRESS))
            Toast.makeText(this, "BTC address copied", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object { const val BTC_ADDRESS = "bc1q03admc0edr4wqrysdc6mvzksqldh44mna2h2fq" }
}
