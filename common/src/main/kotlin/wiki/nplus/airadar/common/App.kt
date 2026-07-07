package wiki.nplus.airadar.common

import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Startup guard for every app entrypoint. The consume loop is asynchronous
 * (`basicConsume` returns immediately) and the metrics HTTP server runs a
 * non-daemon thread, so a failure DURING startup — e.g. Postgres not yet
 * reachable at boot — would leave the JVM alive with the metrics port bound
 * but no RabbitMQ consumer ever registered: a zombie that systemd reports as
 * `active running` while silently processing nothing (queue `consumers=0`).
 *
 * Wrapping main() here turns any startup failure into a non-zero exit so
 * `Restart=on-failure` actually recovers once the dependency is up.
 */
object App {
    fun main(name: String, body: () -> Unit) {
        try {
            body()
        } catch (e: Throwable) {
            LoggerFactory.getLogger(name).error("{}: fatal startup failure, exiting(1) for restart", name, e)
            exitProcess(1)
        }
    }
}
