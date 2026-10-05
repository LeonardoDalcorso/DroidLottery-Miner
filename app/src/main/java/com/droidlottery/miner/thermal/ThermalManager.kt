package com.droidlottery.miner.thermal

enum class ThermalState { NORMAL, WARM, HOT, CRITICAL }

data class ThermalPolicy(
    val mineOnlyCharging: Boolean = false,
    val minimumBatteryPercent: Int = 40,
    val cpuLimitPercent: Int = 50,
)

class ThermalManager(private val policy: ThermalPolicy = ThermalPolicy()) {
    fun throttleFor(state: ThermalState): Int = when (state) {
        ThermalState.NORMAL -> policy.cpuLimitPercent
        ThermalState.WARM -> policy.cpuLimitPercent * 80 / 100
        ThermalState.HOT -> policy.cpuLimitPercent / 2
        ThermalState.CRITICAL -> 0
    }

    fun canStart(batteryPercent: Int, isCharging: Boolean): Boolean =
        batteryPercent >= policy.minimumBatteryPercent && (!policy.mineOnlyCharging || isCharging)
}
