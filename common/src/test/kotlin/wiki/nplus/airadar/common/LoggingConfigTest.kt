package wiki.nplus.airadar.common

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards `common/src/main/resources/logback.xml`.
 *
 * With no configuration at all logback defaults to root=DEBUG, and that is not
 * a theoretical failure: prod ran that way until 2026-07-20, so `docker logs`
 * was HikariCP pool statistics every 30 seconds and the application's own lines
 * were unfindable in the noise. Losing this file again would be silent — the
 * build stays green and only the next incident notices.
 */
class LoggingConfigTest {

    private val context get() = LoggerFactory.getILoggerFactory() as LoggerContext

    @Test
    fun `the config is on the classpath and root is not DEBUG`() {
        assertEquals(Level.INFO, context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).level)
    }

    @Test
    fun `infrastructure heartbeats are muted`() {
        listOf("com.zaxxer.hikari", "com.rabbitmq", "org.postgresql").forEach { name ->
            val level = context.getLogger(name).level
            assertEquals(Level.WARN, level, "$name should be muted to WARN, was $level")
        }
    }

    @Test
    fun `timestamps carry the date and are UTC`() {
        // Every business rule here is keyed on the UTC day, while the container
        // clock is Asia/Taipei — a local time without a date cannot be lined up
        // against essays.day or the daily budget window.
        val pattern = javaClass.getResource("/logback.xml")!!.readText()
        assertTrue(pattern.contains("yyyy-MM-dd"), "log pattern must include the date")
        assertTrue(pattern.contains("UTC"), "log pattern must render in UTC")
    }
}
