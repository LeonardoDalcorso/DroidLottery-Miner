package com.droidlottery.miner.mining.controller

import com.droidlottery.miner.core.model.DefaultProfiles
import com.droidlottery.miner.thermal.ThermalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiningControllerTest {
    @Test fun `startsValidProfileWhenChargingAndBatteryIsSufficient`() {
        val controller = MiningController()

        val result = controller.start(DefaultProfiles.publicPoolBtcSolo, batteryPercent = 80, isCharging = true)

        assertTrue(result.isSuccess)
        assertEquals(MiningStatus.RUNNING, controller.snapshot.status)
    }

    @Test fun `stopsMining_whenThermalStateIsCritical`() {
        val controller = MiningController()
        controller.start(DefaultProfiles.publicPoolBtcSolo, batteryPercent = 80, isCharging = true)

        controller.handleThermalState(ThermalState.CRITICAL)

        assertEquals(MiningStatus.THERMAL_STOPPED, controller.snapshot.status)
    }
}
