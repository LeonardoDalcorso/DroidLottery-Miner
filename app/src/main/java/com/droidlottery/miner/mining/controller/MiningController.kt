package com.droidlottery.miner.mining.controller

import com.droidlottery.miner.core.model.MiningProfile
import com.droidlottery.miner.thermal.ThermalManager
import com.droidlottery.miner.thermal.ThermalState

enum class MiningStatus { STOPPED, RUNNING, PAUSED, THERMAL_STOPPED }

data class MiningSnapshot(
    val status: MiningStatus = MiningStatus.STOPPED,
    val hashrate: String = "0 H/s",
    val acceptedShares: Long = 0,
    val rejectedShares: Long = 0,
    val thermalState: ThermalState = ThermalState.NORMAL,
)

/** Coordinates lifecycle and safeguards. Network and native engines are attached in later sprints. */
class MiningController(private val thermalManager: ThermalManager = ThermalManager()) {
    private var activeProfile: MiningProfile? = null
    var snapshot: MiningSnapshot = MiningSnapshot()
        private set

    fun start(profile: MiningProfile, batteryPercent: Int, isCharging: Boolean): Result<Unit> {
        profile.validationError()?.let { return Result.failure(IllegalArgumentException(it)) }
        if (!thermalManager.canStart(batteryPercent, isCharging)) {
            return Result.failure(IllegalStateException("Charging and minimum-battery policy prevents start."))
        }
        activeProfile = profile
        snapshot = snapshot.copy(status = MiningStatus.RUNNING)
        return Result.success(Unit)
    }

    fun stop() {
        activeProfile = null
        snapshot = MiningSnapshot(thermalState = snapshot.thermalState)
    }

    fun pause() {
        if (snapshot.status == MiningStatus.RUNNING) snapshot = snapshot.copy(status = MiningStatus.PAUSED)
    }

    fun resume() {
        if (activeProfile != null && snapshot.status == MiningStatus.PAUSED) {
            snapshot = snapshot.copy(status = MiningStatus.RUNNING)
        }
    }

    fun handleThermalState(state: ThermalState) {
        snapshot = snapshot.copy(
            thermalState = state,
            status = if (state == ThermalState.CRITICAL) MiningStatus.THERMAL_STOPPED else snapshot.status,
        )
    }
}
