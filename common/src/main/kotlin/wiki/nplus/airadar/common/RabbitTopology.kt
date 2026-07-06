package wiki.nplus.airadar.common

/**
 * Single source of truth for broker topology names.
 *
 * Retry ladder (ADR-004): each work queue gets tiered wait-queues
 * (`retry.<tier>.<origin>`) that have no consumers, only a message TTL and a
 * dead-letter policy pointing back at the origin queue via the default
 * exchange. The attempt count travels in the `x-retry-count` header; when it
 * exceeds [MAX_RETRIES], or the failure is non-retryable, the message parks in
 * [DLQ].
 */
object RabbitTopology {
    const val INGEST_EXCHANGE = "ingest.x"

    const val INGEST_QUEUE = "ingest.q"
    const val DIGEST_QUEUE = "digest.q"
    const val PUBLISH_QUEUE = "publish.q"
    const val DLQ = "dlq.q"

    val WORK_QUEUES = listOf(INGEST_QUEUE, DIGEST_QUEUE, PUBLISH_QUEUE)

    const val RETRY_COUNT_HEADER = "x-retry-count"
    const val ORIGIN_QUEUE_HEADER = "x-origin-queue"
    const val MAX_RETRIES = 3

    val RETRY_TIERS = listOf(
        RetryTier("30s", ttlMillis = 30_000),
        RetryTier("5m", ttlMillis = 300_000),
        RetryTier("1h", ttlMillis = 3_600_000),
    )

    fun routingKey(source: String): String = "item.$source"

    /** Wait-queue name for a given origin queue and 1-based attempt. */
    fun retryQueue(originQueue: String, attempt: Int): String =
        "retry.${tierFor(attempt).name}.$originQueue"

    fun tierFor(attempt: Int): RetryTier = RETRY_TIERS[(attempt - 1).coerceIn(0, RETRY_TIERS.lastIndex)]

    data class RetryTier(val name: String, val ttlMillis: Long)
}
