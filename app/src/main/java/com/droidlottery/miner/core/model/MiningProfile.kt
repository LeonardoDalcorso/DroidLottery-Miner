package com.droidlottery.miner.core.model

enum class MiningMode { POOL, LOTTERY_SOLO }
enum class PoolProvider { BINANCE, PUBLIC_POOL, CUSTOM }
enum class MiningAlgorithm { SHA256D, SCRYPT, KAWPOW, ETCHASH }
enum class Coin { BTC, BCH, LTC, RVN, ETC }
enum class RewardDestination { BINANCE_POOL, EXTERNAL_WALLET }

data class MiningProfile(
    val id: String,
    val name: String,
    val coin: Coin,
    val algorithm: MiningAlgorithm,
    val mode: MiningMode,
    val provider: PoolProvider,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val usernameTemplate: String,
    val password: String? = null,
    val rewardDestination: RewardDestination,
    val walletAddress: String? = null,
    val enabled: Boolean = true,
) {
    fun validationError(): String? = when {
        id.isBlank() || name.isBlank() -> "Profile id and name are required."
        host.isBlank() -> "Pool host is required."
        port !in 1..65535 -> "Pool port must be between 1 and 65535."
        usernameTemplate.isBlank() -> "Username template is required."
        mode == MiningMode.LOTTERY_SOLO && walletAddress.isNullOrBlank() ->
            "A public wallet address is required for solo mode."
        else -> null
    }
}

object DefaultProfiles {
    val publicPoolBtcSolo = MiningProfile(
        id = "publicpool-btc-solo",
        name = "Public Pool BTC Solo",
        coin = Coin.BTC,
        algorithm = MiningAlgorithm.SHA256D,
        mode = MiningMode.LOTTERY_SOLO,
        provider = PoolProvider.PUBLIC_POOL,
        host = "public-pool.io",
        port = 3333,
        tls = false,
        usernameTemplate = "bc1q03admc0edr4wqrysdc6mvzksqldh44mna2h2fq.droid01",
        password = "x",
        rewardDestination = RewardDestination.EXTERNAL_WALLET,
        walletAddress = "bc1q03admc0edr4wqrysdc6mvzksqldh44mna2h2fq",
    )
}
