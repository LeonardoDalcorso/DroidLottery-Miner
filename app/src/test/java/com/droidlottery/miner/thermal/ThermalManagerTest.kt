package com.droidlottery.miner.thermal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalManagerTest {
    private val manager = ThermalManager()

    @Test fun `stopsMining_whenThermalLimitIsExceeded`() {
        assertEquals(0, manager.throttleFor(ThermalState.CRITICAL))
    }

    @Test fun `requiresMinimumBatteryButAllowsManualStartOffCharger`() {
        assertFalse(manager.canStart(batteryPercent = 39, isCharging = true))
        assertTrue(manager.canStart(batteryPercent = 80, isCharging = false))
        assertTrue(manager.canStart(batteryPercent = 80, isCharging = true))
    }
}
