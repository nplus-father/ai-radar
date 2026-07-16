package wiki.nplus.airadar.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Payload passed between pipeline stages after an item is persisted.
 * [kind] tells the publisher what to render: "item" (default, omitted on the
 * wire) regenerates digest pages, "essay" renders the item's daily essay.
 * Additive change only — old messages without the field decode as "item".
 */
@Serializable
data class StageMessage(val itemId: Long, val kind: String = "item") {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(body: String): StageMessage = json.decodeFromString(serializer(), body)
    }
}
