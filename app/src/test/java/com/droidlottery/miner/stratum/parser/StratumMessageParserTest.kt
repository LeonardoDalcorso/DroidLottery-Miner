package com.droidlottery.miner.stratum.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StratumMessageParserTest {
    @Test fun `buffersFragmentedStratumMessage`() {
        val parser = StratumMessageParser()

        assertTrue(parser.append("{\"id\":1,\"result\":tr").isEmpty())
        val messages = parser.append("ue,\"error\":null}\n")

        assertEquals(1, messages.size)
        assertEquals(1L, messages.single().id)
        assertEquals(true, messages.single().result)
    }

    @Test fun `parsesMultipleMessagesFromSingleBuffer`() {
        val parser = StratumMessageParser()
        val messages = parser.append("{\"method\":\"mining.notify\",\"params\":[]}\n{\"id\":2,\"result\":true}\n")

        assertEquals(2, messages.size)
        assertEquals("mining.notify", messages.first().method)
        assertEquals(2L, messages.last().id)
    }
}
