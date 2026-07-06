package wiki.nplus.airadar.common

/**
 * Single source of truth for broker topology names.
 * The retry tiers implement the TTL + DLX ladder described in ADR-004:
 * retry queues have no consumers; expired messages dead-letter back to the
 * ingest exchange with their original routing key.
 */
object RabbitTopology {
    const val INGEST_EXCHANGE = "ingest.x"

    const val INGEST_QUEUE = "ingest.q"
    const val DIGEST_QUEUE = "digest.q"
    const val PUBLISH_QUEUE = "publish.q"
    const val DLQ = "dlq.q"

    const val RETRY_COUNT_HEADER = "x-retry-count"
    const val MAX_RETRIES = 3

    /** Retry tier queue name for a given attempt (1-based). */
    val RETRY_TIERS = listOf(
        RetryTier("retry.30s.q", ttlMillis = 30_000),
        RetryTier("retry.5m.q", ttlMillis = 300_000),
        RetryTier("retry.1h.q", ttlMillis = 3_600_000),
    )

    fun routingKey(source: String): String = "item.$source"

    data class RetryTier(val queue: String, val ttlMillis: Long)
}
