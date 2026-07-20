package wiki.nplus.airadar.digester

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyAttemptGuardTest {

    private val day = LocalDate.of(2026, 7, 20)

    @Test
    fun `caps attempts within a day`() {
        val guard = DailyAttemptGuard(3)
        assertEquals(listOf(true, true, true, false, false), (1..5).map { guard.tryConsume(day) })
    }

    @Test
    fun `the next day starts fresh`() {
        val guard = DailyAttemptGuard(2)
        repeat(2) { guard.tryConsume(day) }
        assertFalse(guard.tryConsume(day))
        assertTrue(guard.tryConsume(day.plusDays(1)))
    }
}
