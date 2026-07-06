package wiki.nplus.airadar.common

/** All runtime configuration comes from environment variables (compose .env). */
object Config {
    fun str(name: String, default: String? = null): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: default
            ?: error("Missing required environment variable: $name")

    fun int(name: String, default: Int): Int = System.getenv(name)?.toIntOrNull() ?: default

    fun long(name: String, default: Long): Long = System.getenv(name)?.toLongOrNull() ?: default

    fun double(name: String, default: Double): Double = System.getenv(name)?.toDoubleOrNull() ?: default

    fun bool(name: String, default: Boolean): Boolean = System.getenv(name)?.toBooleanStrictOrNull() ?: default
}
