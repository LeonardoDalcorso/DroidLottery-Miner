package com.droidlottery.miner.mining.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.droidlottery.miner.core.config.ProfileRepository
import com.droidlottery.miner.core.config.PerformanceSettings
import com.droidlottery.miner.core.model.MiningProfile
import com.droidlottery.miner.mining.stats.MiningRuntimeStore
import com.droidlottery.miner.mining.engine.FoundShare
import com.droidlottery.miner.mining.engine.Sha256dEngine
import com.droidlottery.miner.mining.engine.Sha256dListener
import com.droidlottery.miner.stratum.client.StratumClient
import com.droidlottery.miner.stratum.client.StratumListener
import com.droidlottery.miner.stratum.model.StratumJob
import com.droidlottery.miner.stratum.protocol.StratumRequest
import com.droidlottery.miner.stratum.protocol.StratumResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray

class MiningForegroundService : Service() {
    private lateinit var runtime: MiningRuntimeStore
    private var client: StratumClient? = null
    private var engine: Sha256dEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var extraNonce1: String? = null
    private var extraNonce2Size: Int? = null
    private var difficulty = 1.0
    private val nextRequestId = AtomicLong(SUBMIT_ID_START)
    private val submittedShares = ConcurrentHashMap<Long, FoundShare>()
    @Volatile private var shouldRun = false
    private val reconnectExecutor = Executors.newSingleThreadScheduledExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runtime = MiningRuntimeStore(this)
        createChannel()
        when (intent?.action) {
            ACTION_STOP -> stopSession()
            else -> if (!shouldRun) startSession()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (shouldRun) runtime.update(running = false, status = "STOPPED", detail = "Service destroyed by Android")
        client?.disconnect()
        engine?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        reconnectExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startSession() {
        shouldRun = true
        val profile = ProfileRepository(this).load()
        runtime.update(running = true, status = "CONNECTING", detail = "Connecting to ${profile.host}:${profile.port}", startedAtMillis = System.currentTimeMillis(), event = "Mining started")
        startForeground(NOTIFICATION_ID, notification("Connecting to ${profile.host}"))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:MiningSession")
            .apply { acquire() }
        connect(profile, 0)
    }

    private fun connect(profile: MiningProfile, attempt: Int) {
        if (!shouldRun) return
        client = StratumClient(object : StratumListener {
            override fun onConnected() {
                runtime.update(status = "SUBSCRIBING", detail = "Connected; subscribing")
                updateNotification("Connected • subscribing")
                client?.send(StratumRequest(id = SUBSCRIBE_ID, method = "mining.subscribe"))
            }

            override fun onMessage(message: StratumResponse) {
                when {
                    message.id == SUBSCRIBE_ID -> authorize(profile, message)
                    message.id == AUTHORIZE_ID && message.result == true -> {
                        runtime.update(status = "AUTHORIZED", detail = "Waiting for a mining job", event = "Worker authorized by pool")
                        updateNotification("Authorized • waiting for job")
                    }
                    message.id == AUTHORIZE_ID -> {
                        runtime.update(status = "ERROR", detail = "Pool rejected worker authorization")
                    }
                    message.method == "mining.set_difficulty" -> message.params?.let { params ->
                        difficulty = params.optDouble(0, 1.0)
                        runtime.update(status = "AUTHORIZED", detail = "Pool difficulty: $difficulty")
                    }
                    message.method == "mining.notify" -> {
                        val current = runtime.read()
                        startJob(message.params, current.receivedJobs + 1)
                    }
                    message.id != null && submittedShares.remove(message.id) != null -> handleShareResult(message)
                }
            }

            override fun onDisconnected(cause: Throwable?) {
                if (!shouldRun) return
                engine?.stop()
                engine = null
                val delay = BACKOFF_SECONDS[minOf(attempt, BACKOFF_SECONDS.lastIndex)]
                runtime.update(status = "RECONNECTING", detail = "Disconnected; retrying in ${delay}s")
                updateNotification("Reconnecting in ${delay}s")
                reconnectExecutor.schedule({ connect(profile, attempt + 1) }, delay.toLong(), TimeUnit.SECONDS)
            }
        })
        runCatching { client?.connect(profile.host, profile.port, profile.tls) }
            .onFailure { runtime.update(status = "ERROR", detail = it.message ?: "Connection error") }
    }

    private fun stopSession() {
        shouldRun = false
        client?.disconnect()
        client = null
        engine?.stop()
        engine = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        runtime.update(running = false, status = "STOPPED", detail = "Stopped by user", startedAtMillis = 0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(detail: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("DroidLottery Miner")
        .setContentText(detail)
        .setOngoing(true)
        .build()

    private fun updateNotification(detail: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(detail))
    }

    private fun authorize(profile: MiningProfile, message: StratumResponse) {
        val subscribeResult = message.result as? JSONArray
        if (subscribeResult == null || subscribeResult.length() < 3) {
            runtime.update(status = "ERROR", detail = "Pool returned an invalid subscribe response")
            return
        }
        extraNonce1 = subscribeResult.getString(1)
        extraNonce2Size = subscribeResult.getInt(2)
        client?.send(
            StratumRequest(AUTHORIZE_ID, "mining.authorize", JSONArray().put(profile.usernameTemplate).put(profile.password ?: "x")),
        )
    }

    private fun startJob(params: JSONArray?, jobCount: Long) {
        val nonce1 = extraNonce1
        val nonce2Size = extraNonce2Size
        if (params == null || nonce1 == null || nonce2Size == null) {
            runtime.update(status = "ERROR", detail = "Received job before subscribe completed")
            return
        }
        runCatching {
            val job = StratumJob.fromNotify(params)
            if (engine == null) engine = Sha256dEngine(engineListener, PerformanceSettings(this).threadCount())
            engine?.start(job, nonce1, nonce2Size, difficulty)
            runtime.update(status = "MINING", detail = "Mining job ${job.id}", receivedJobs = jobCount, event = "Mining job received")
            updateNotification("Mining • ${runtime.read().hashrate}")
        }.onFailure { error -> runtime.update(status = "ERROR", detail = error.message ?: "Invalid mining job") }
    }

    private fun submitShare(share: FoundShare) {
        val requestId = nextRequestId.getAndIncrement()
        submittedShares[requestId] = share
        if (share.blockCandidate) {
            val current = runtime.read()
            runtime.update(blockCandidates = current.blockCandidates + 1, detail = "Block candidate found; submitting")
        }
        val profile = ProfileRepository(this).load()
        runCatching {
            client?.send(
                StratumRequest(
                    requestId,
                    "mining.submit",
                    JSONArray().put(profile.usernameTemplate).put(share.jobId).put(share.extraNonce2).put(share.time).put(share.nonce),
                ),
            )
        }.onFailure {
            submittedShares.remove(requestId)
            val current = runtime.read()
            runtime.update(rejectedShares = current.rejectedShares + 1, detail = "Share submission failed: ${it.message}")
        }
    }

    private fun handleShareResult(message: StratumResponse) {
        val current = runtime.read()
        if (message.result == true) {
            runtime.update(acceptedShares = current.acceptedShares + 1, detail = "Share accepted", event = "Share accepted by pool")
        } else {
            runtime.update(rejectedShares = current.rejectedShares + 1, detail = "Share rejected", event = "Share rejected by pool")
        }
    }

    private val engineListener = object : Sha256dListener {
        override fun onHashrate(hashesPerSecond: Double) {
            val rate = formatHashrate(hashesPerSecond)
            runtime.update(status = "MINING", detail = "Hashing", hashrate = rate, hashrateValue = hashesPerSecond)
            updateNotification("Mining • $rate")
        }

        override fun onShareFound(share: FoundShare) = submitShare(share)

        override fun onEngineError(error: Throwable) {
            runtime.update(status = "ERROR", detail = "SHA-256d engine: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun formatHashrate(value: Double): String = when {
        value >= 1_000_000_000 -> "%.2f GH/s".format(value / 1_000_000_000)
        value >= 1_000_000 -> "%.2f MH/s".format(value / 1_000_000)
        value >= 1_000 -> "%.2f kH/s".format(value / 1_000)
        else -> "%.0f H/s".format(value)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Mining status", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "com.droidlottery.miner.STOP"
        const val CHANNEL_ID = "mining_status"
        const val NOTIFICATION_ID = 10
        private const val SUBSCRIBE_ID = 1L
        private const val AUTHORIZE_ID = 2L
        private const val SUBMIT_ID_START = 10L
        private val BACKOFF_SECONDS = intArrayOf(2, 5, 10, 20, 30, 60)
    }
}
