package pl.jojczak.penmouses.service

import org.junit.Assert.*
import org.junit.Test

class SamsungPenBatteryCandidateGuardTest {
    @Test fun requiresTwoMatchingReadings() {
        val guard = SamsungPenBatteryCandidateGuard()
        assertFalse(guard.observe(100, 1000L))
        assertTrue(guard.observe(100, 2300L))
    }

    @Test fun ignoresOneOffContaminatedValue() {
        val guard = SamsungPenBatteryCandidateGuard()
        assertFalse(guard.observe(80, 1000L))
        assertFalse(guard.observe(100, 5000L))
        assertTrue(guard.observe(100, 6000L))
    }

    @Test fun rejectsOldOrDuplicatedMeasurements() {
        val guard = SamsungPenBatteryCandidateGuard()
        assertFalse(guard.observe(75, 1000L))
        assertFalse(guard.observe(75, 1000L))
        assertFalse(guard.observe(75, 40000L))
        assertTrue(guard.observe(75, 42000L))
    }

    @Test fun resetPreventsCrossScreenMatches() {
        val guard = SamsungPenBatteryCandidateGuard()
        guard.observe(40, 1000L)
        guard.reset()
        assertFalse(guard.observe(40, 1800L))
        assertTrue(guard.observe(40, 2800L))
    }
}
