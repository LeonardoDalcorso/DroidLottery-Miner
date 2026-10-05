package com.droidlottery.miner.core.config

import android.content.Context
import com.droidlottery.miner.core.model.Coin
import com.droidlottery.miner.core.model.DefaultProfiles
import com.droidlottery.miner.core.model.MiningAlgorithm
import com.droidlottery.miner.core.model.MiningMode
import com.droidlottery.miner.core.model.MiningProfile
import com.droidlottery.miner.core.model.PoolProvider
import com.droidlottery.miner.core.model.RewardDestination
import org.json.JSONObject

/** Local-only store. Credentials are deliberately not persisted in this MVP. */
class ProfileRepository(context: Context) {
    private val preferences = context.getSharedPreferences("mining_profiles", Context.MODE_PRIVATE)

    fun load(): MiningProfile = preferences.getString(PROFILE_KEY, null)
        ?.let(::decode)
        ?.let(::migrateInvalidDefaultAddress)
        ?.takeUnless { it.id == "demo-btc" }
        ?: DefaultProfiles.publicPoolBtcSolo

    private fun migrateInvalidDefaultAddress(profile: MiningProfile): MiningProfile {
        val invalidAddress = "bc1q03admc0edr4wgrysdc6mvzksqldh44mna2h2fq"
        val validAddress = "bc1q03admc0edr4wqrysdc6mvzksqldh44mna2h2fq"
        return if (profile.walletAddress == invalidAddress || profile.usernameTemplate.startsWith(invalidAddress)) {
            profile.copy(
                walletAddress = validAddress,
                usernameTemplate = profile.usernameTemplate.replace(invalidAddress, validAddress),
            )
        } else profile
    }

    fun save(profile: MiningProfile) {
        require(profile.validationError() == null) { profile.validationError()!! }
        preferences.edit().putString(PROFILE_KEY, encode(profile)).apply()
    }

    private fun encode(profile: MiningProfile) = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("coin", profile.coin.name)
        put("algorithm", profile.algorithm.name)
        put("mode", profile.mode.name)
        put("provider", profile.provider.name)
        put("host", profile.host)
        put("port", profile.port)
        put("tls", profile.tls)
        put("usernameTemplate", profile.usernameTemplate)
        put("rewardDestination", profile.rewardDestination.name)
        put("walletAddress", profile.walletAddress)
        put("enabled", profile.enabled)
    }.toString()

    private fun decode(raw: String): MiningProfile {
        val json = JSONObject(raw)
        return MiningProfile(
            id = json.getString("id"), name = json.getString("name"),
            coin = Coin.valueOf(json.getString("coin")),
            algorithm = MiningAlgorithm.valueOf(json.getString("algorithm")),
            mode = MiningMode.valueOf(json.getString("mode")),
            provider = PoolProvider.valueOf(json.getString("provider")),
            host = json.getString("host"), port = json.getInt("port"),
            tls = json.getBoolean("tls"), usernameTemplate = json.getString("usernameTemplate"),
            rewardDestination = RewardDestination.valueOf(json.getString("rewardDestination")),
            walletAddress = json.optString("walletAddress").takeIf { it.isNotBlank() },
            enabled = json.optBoolean("enabled", true),
        )
    }

    private companion object { const val PROFILE_KEY = "active_profile" }
}
