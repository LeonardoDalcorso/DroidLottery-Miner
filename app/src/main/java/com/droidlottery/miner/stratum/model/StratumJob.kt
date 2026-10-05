package com.droidlottery.miner.stratum.model

import org.json.JSONArray

data class StratumJob(
    val id: String,
    val previousHash: String,
    val coinbasePart1: String,
    val coinbasePart2: String,
    val merkleBranches: List<String>,
    val version: String,
    val bits: String,
    val time: String,
    val cleanJobs: Boolean,
) {
    companion object {
        fun fromNotify(params: JSONArray): StratumJob = StratumJob(
            id = params.getString(0),
            previousHash = params.getString(1),
            coinbasePart1 = params.getString(2),
            coinbasePart2 = params.getString(3),
            merkleBranches = params.getJSONArray(4).let { branches ->
                List(branches.length()) { branches.getString(it) }
            },
            version = params.getString(5),
            bits = params.getString(6),
            time = params.getString(7),
            cleanJobs = params.optBoolean(8, true),
        )
    }
}
