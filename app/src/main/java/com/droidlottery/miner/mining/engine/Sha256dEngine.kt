package com.droidlottery.miner.mining.engine

import com.droidlottery.miner.stratum.model.StratumJob
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

data class FoundShare(val jobId: String, val extraNonce2: String, val time: String, val nonce: String, val blockCandidate: Boolean)

interface Sha256dListener {
    fun onHashrate(hashesPerSecond: Double)
    fun onShareFound(share: FoundShare)
    fun onEngineError(error: Throwable)
}

/**
 * CPU-only SHA-256d reference engine for Stratum V1. It performs real hashes and target checks;
 * it is intentionally kept separate from the future NDK-optimized worker.
 */
class Sha256dEngine(
    private val listener: Sha256dListener,
    threadCount: Int = max(1, Runtime.getRuntime().availableProcessors()),
) {
    private val workers = threadCount.coerceIn(1, max(1, Runtime.getRuntime().availableProcessors()))
    private val executor = Executors.newFixedThreadPool(workers) { runnable ->
        Thread(runnable, "sha256d-worker").apply { isDaemon = true }
    }
    private val active = AtomicBoolean(true)
    private val generation = AtomicLong(0)
    private val totalHashes = AtomicLong(0)
    private val reportLock = Any()
    private var lastReportNanos = System.nanoTime()
    private var lastReportedHashes = 0L

    fun start(job: StratumJob, extraNonce1: String, extraNonce2Size: Int, difficulty: Double) {
        require(extraNonce2Size in 1..16) { "Unsupported extranonce2 size: $extraNonce2Size" }
        val work = Work(job, extraNonce1, extraNonce2Size, shareTarget(difficulty), compactTarget(job.bits))
        val jobGeneration = generation.incrementAndGet()
        repeat(workers) { workerIndex -> executor.execute { mine(work, jobGeneration, workerIndex) } }
    }

    fun stop() {
        active.set(false)
        generation.incrementAndGet()
        executor.shutdownNow()
    }

    private fun mine(work: Work, jobGeneration: Long, workerIndex: Int) {
        try {
            val extraNonce2 = workerExtraNonce(workerIndex, work.extraNonce2Size)
            val header = buildHeader(work.job, work.extraNonce1, extraNonce2)
            var nonce = workerIndex.toLong()
            var localHashes = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            while (active.get() && generation.get() == jobGeneration) {
                writeUInt32Le(header, 76, nonce)
                val first = digest.digest(header)
                val hash = digest.digest(first)
                localHashes++
                if (isAtMostTarget(hash, work.shareTarget)) {
                    val candidate = isAtMostTarget(hash, work.networkTarget)
                    listener.onShareFound(FoundShare(work.job.id, extraNonce2, work.job.time, nonce.toUInt().toString(16).padStart(8, '0'), candidate))
                }
                nonce = (nonce + workers) and 0xffff_ffffL
                if (localHashes >= HASH_BATCH_SIZE) {
                    report(localHashes)
                    localHashes = 0
                }
            }
            if (localHashes > 0) report(localHashes)
        } catch (error: Throwable) {
            if (active.get()) listener.onEngineError(error)
        }
    }

    private fun report(hashes: Long) {
        totalHashes.addAndGet(hashes)
        synchronized(reportLock) {
            val now = System.nanoTime()
            val elapsed = now - lastReportNanos
            if (elapsed >= REPORT_INTERVAL_NANOS) {
                val current = totalHashes.get()
                listener.onHashrate((current - lastReportedHashes) * 1_000_000_000.0 / elapsed)
                lastReportedHashes = current
                lastReportNanos = now
            }
        }
    }

    private fun buildHeader(job: StratumJob, extraNonce1: String, extraNonce2: String): ByteArray {
        var merkleRoot = sha256d(hexToBytes(job.coinbasePart1 + extraNonce1 + extraNonce2 + job.coinbasePart2))
        job.merkleBranches.forEach { merkleRoot = sha256d(merkleRoot + hexToBytes(it)) }
        return swapWords(hexToBytes(job.version)) +
            swapWords(hexToBytes(job.previousHash)) +
            merkleRoot +
            swapWords(hexToBytes(job.time)) +
            swapWords(hexToBytes(job.bits)) +
            ByteArray(4)
    }

    private fun sha256d(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(input))
    }

    private fun shareTarget(difficulty: Double): ByteArray {
        val safeDifficulty = difficulty.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val target = BigDecimal(DIFF1_TARGET).divide(BigDecimal.valueOf(safeDifficulty), 0, RoundingMode.DOWN).toBigInteger()
        return target.toFixedBytes()
    }

    private fun compactTarget(bits: String): ByteArray {
        val compact = bits.toLong(16)
        val exponent = ((compact shr 24) and 0xff).toInt()
        val mantissa = compact and 0x007f_ffffL
        val target = BigInteger.valueOf(mantissa).shiftLeft(8 * (exponent - 3))
        return target.toFixedBytes()
    }

    private fun isAtMostTarget(hash: ByteArray, target: ByteArray): Boolean {
        for (index in hash.indices) {
            val value = hash[hash.lastIndex - index].toInt() and 0xff
            val limit = target[index].toInt() and 0xff
            if (value != limit) return value < limit
        }
        return true
    }

    private fun workerExtraNonce(workerIndex: Int, size: Int): String =
        workerIndex.toLong().toString(16).padStart(size * 2, '0').takeLast(size * 2)

    private fun writeUInt32Le(target: ByteArray, offset: Int, value: Long) {
        repeat(4) { target[offset + it] = ((value shr (it * 8)) and 0xff).toByte() }
    }

    private fun swapWords(bytes: ByteArray): ByteArray {
        require(bytes.size % 4 == 0) { "Expected complete 32-bit words" }
        return ByteArray(bytes.size).also { output ->
            bytes.indices.forEach { index -> output[index] = bytes[index / 4 * 4 + 3 - index % 4] }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0 && hex.all { it.digitToIntOrNull(16) != null }) { "Invalid hexadecimal data" }
        return ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun BigInteger.toFixedBytes(): ByteArray {
        val signed = toByteArray()
        val raw = if (signed.size > 32) signed.copyOfRange(signed.size - 32, signed.size) else signed
        return ByteArray(32).also { raw.copyInto(it, 32 - raw.size) }
    }

    private data class Work(
        val job: StratumJob,
        val extraNonce1: String,
        val extraNonce2Size: Int,
        val shareTarget: ByteArray,
        val networkTarget: ByteArray,
    )

    private companion object {
        const val HASH_BATCH_SIZE = 2_048L
        const val REPORT_INTERVAL_NANOS = 1_000_000_000L
        val DIFF1_TARGET = BigInteger("00000000FFFF0000000000000000000000000000000000000000000000000000", 16)
    }
}
