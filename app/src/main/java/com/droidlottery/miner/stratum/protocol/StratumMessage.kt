package com.droidlottery.miner.stratum.protocol

import org.json.JSONArray
import org.json.JSONObject

data class StratumRequest(val id: Long, val method: String, val params: JSONArray = JSONArray()) {
    fun toJson(): String = JSONObject().apply {
        put("id", id); put("method", method); put("params", params)
    }.toString()
}

data class StratumResponse(
    val id: Long?,
    val method: String?,
    val result: Any?,
    val error: Any?,
    val params: JSONArray?,
)
