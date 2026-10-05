package com.droidlottery.miner.core.config

import android.content.Context

/** CPU allocation only: SHA-256d does not benefit from reserving additional application memory. */
class PerformanceSettings(context: Context) {
    private val preferences = context.getSharedPreferences("mining_performance", Context.MODE_PRIVATE)
    private val maxThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    fun threadCount(): Int = preferences.getInt("thread_count", maxThreads).coerceIn(1, maxThreads)

    fun saveThreadCount(threads: Int) {
        preferences.edit().putInt("thread_count", threads.coerceIn(1, maxThreads)).apply()
    }

    fun availableProcessors(): Int = maxThreads
}
