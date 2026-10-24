package dev.pov.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DebridPriorityTest {
    @Test fun `existing selected service becomes first priority`() {
        assertEquals(listOf("offcloud", "torbox"), DebridServices.parsePriority("", "offcloud"))
        assertEquals(listOf("torbox", "offcloud"), DebridServices.parsePriority("", "torbox"))
    }
    @Test fun `saved priority takes precedence over legacy selection`() {
        assertEquals(listOf("offcloud", "torbox"), DebridServices.parsePriority("offcloud,torbox", "torbox"))
        assertEquals(listOf("torbox", "offcloud"), DebridServices.parsePriority("torbox,offcloud", "offcloud"))
    }
    @Test fun `invalid duplicate and missing entries cannot disable a connected service`() {
        assertEquals(listOf("torbox", "offcloud"), DebridServices.parsePriority("unknown,torbox,torbox", "offcloud"))
        assertEquals(listOf("offcloud", "torbox"), DebridServices.parsePriority("unknown", "offcloud"))
    }
}
