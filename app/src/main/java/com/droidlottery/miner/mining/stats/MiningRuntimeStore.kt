package com.droidlottery.miner.mining.stats

import android.content.Context
import java.text.DateFormat
import java.util.Date

data class MiningRuntimeState(
    val running: Boolean,
    val status: String,
    val detail: String,
    val receivedJobs: Long,
    val acceptedShares: Long,
    val rejectedShares: Long,
    val blockCandidates: Long,
    val hashrate: String,
    val startedAtMillis: Long,
)

/** A small observable-by-polling state store shared by the foreground service and UI. */
class MiningRuntimeStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("mining_runtime", Context.MODE_PRIVATE)

    fun read() = MiningRuntimeState(
        running = preferences.getBoolean("running", false),
        status = preferences.getString("status", "STOPPED") ?: "STOPPED",
        detail = preferences.getString("detail", "Not running") ?: "Not running",
        receivedJobs = preferences.getLong("jobs", 0),
        acceptedShares = preferences.getLong("accepted", 0),
        rejectedShares = preferences.getLong("rejected", 0),
        blockCandidates = preferences.getLong("candidates", 0),
        hashrate = preferences.getString("hashrate", "0 H/s") ?: "0 H/s",
        startedAtMillis = preferences.getLong("started_at", 0),
    )

    fun readHashrateHistory(): List<Float> = preferences.getString("hashrate_history", "")
        .orEmpty().split(',').mapNotNull { it.toFloatOrNull() }

    fun readEvents(): List<String> = preferences.getString("events", "")
        .orEmpty().split(EVENT_SEPARATOR).filter { it.isNotBlank() }.asReversed()

    fun update(
        running: Boolean = read().running,
        status: String = read().status,
        detail: String = read().detail,
        receivedJobs: Long = read().receivedJobs,
        acceptedShares: Long = read().acceptedShares,
        rejectedShares: Long = read().rejectedShares,
        blockCandidates: Long = read().blockCandidates,
        hashrate: String = read().hashrate,
        hashrateValue: Double? = null,
        startedAtMillis: Long = read().startedAtMillis,
        event: String? = null,
    ) {
        val editor = preferences.edit()
            .putBoolean("running", running)
            .putString("status", status)
            .putString("detail", detail)
            .putLong("jobs", receivedJobs)
            .putLong("accepted", acceptedShares)
            .putLong("rejected", rejectedShares)
            .putLong("candidates", blockCandidates)
            .putString("hashrate", hashrate)
            .putLong("started_at", startedAtMillis)
        hashrateValue?.let { value ->
            val samples = (readHashrateHistory() + value.toFloat()).takeLast(MAX_SAMPLES)
            editor.putString("hashrate_history", samples.joinToString(","))
        }
        event?.let { message ->
            val entry = "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date())} — $message"
            editor.putString("events", (preferences.getString("events", "").orEmpty().split(EVENT_SEPARATOR) + entry).takeLast(MAX_EVENTS).joinToString(EVENT_SEPARATOR))
        }
        editor.apply()
        context.sendBroadcast(android.content.Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.droidlottery.miner.MINING_STATE_CHANGED"
        private const val MAX_SAMPLES = 60
        private const val MAX_EVENTS = 80
        private const val EVENT_SEPARATOR = "\n"
    }
}
