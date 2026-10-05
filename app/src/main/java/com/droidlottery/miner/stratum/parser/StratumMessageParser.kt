package com.droidlottery.miner.stratum.parser

import com.droidlottery.miner.stratum.protocol.StratumResponse
import org.json.JSONArray
import org.json.JSONObject

/** Buffers newline-delimited Stratum V1 JSON messages, including fragmented TCP reads. */
class StratumMessageParser {
    private val buffer = StringBuilder()

    fun append(chunk: String): List<StratumResponse> {
        buffer.append(chunk)
        val messages = mutableListOf<StratumResponse>()
        while (true) {
            val newline = buffer.indexOf("\n")
            if (newline < 0) return messages
            val line = buffer.substring(0, newline).trim()
            buffer.delete(0, newline + 1)
            if (line.isNotEmpty()) messages += parse(line)
        }
    }

    private fun parse(line: String): StratumResponse {
        val json = JSONObject(line)
        return StratumResponse(
            id = json.optLong("id").takeIf { json.has("id") && !json.isNull("id") },
            method = json.optString("method").takeIf { it.isNotEmpty() },
            result = json.opt("result"),
            error = json.opt("error").takeUnless { it == JSONObject.NULL },
            params = json.opt("params") as? JSONArray,
        )
    }
}
