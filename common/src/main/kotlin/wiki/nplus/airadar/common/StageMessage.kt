package wiki.nplus.airadar.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Payload passed between pipeline stages after an item is persisted. */
@Serializable
data class StageMessage(val itemId: Long) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(body: String): StageMessage = json.decodeFromString(serializer(), body)
    }
}
