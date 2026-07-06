package wiki.nplus.airadar.common

import java.net.URI
import java.security.MessageDigest

/**
 * Cross-source dedup, layer two (layer one is the DB unique key on
 * (source, external_id) — see ADR-003). Two sources linking the same article
 * must produce the same canonical URL and therefore the same content hash.
 */
object UrlCanonicalizer {

    private val TRACKING_PARAM_PREFIXES = listOf("utm_", "fbclid", "gclid", "ref_", "mc_")

    fun canonicalize(rawUrl: String): String {
        val uri = URI(rawUrl.trim())
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return rawUrl.trim()
        val path = uri.path.orEmpty().trimEnd('/')
        val query = uri.query
            ?.split('&')
            ?.filterNot { param -> TRACKING_PARAM_PREFIXES.any { param.startsWith(it) } }
            ?.sorted()
            ?.joinToString("&")
            ?.takeIf { it.isNotEmpty() }
        // Scheme and fragment are dropped on purpose: http/https variants and
        // in-page anchors still identify the same document.
        return buildString {
            append(host)
            append(path)
            if (query != null) append('?').append(query)
        }
    }

    fun contentHash(title: String, rawUrl: String): String {
        val normalizedTitle = title.trim().lowercase().replace(Regex("\\s+"), " ")
        val input = "$normalizedTitle|${canonicalize(rawUrl)}"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
