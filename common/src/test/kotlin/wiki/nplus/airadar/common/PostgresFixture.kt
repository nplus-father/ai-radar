package wiki.nplus.airadar.common

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import javax.sql.DataSource

/**
 * A real Postgres with the real migrations applied, shared by every SQL test in
 * this module.
 *
 * Why a container rather than mocks: every one of ItemRepository's 35 queries is
 * a raw JDBC string typed against a schema the Kotlin compiler cannot see. A
 * mocked DataSource can only assert that the string equals the string we wrote —
 * it cannot catch a column that does not exist, a CHECK constraint no migration
 * added, or a JOIN that quietly excludes rows. Those are precisely the bugs this
 * project has actually shipped: the curator/essayist pools disagreed on
 * LEFT vs INNER JOIN and no essay could ever be published (docs/next-steps.md),
 * and the retired critic gate wrote `llm_usage.purpose` values the constraint
 * rejected, burning the daily budget every night for two days.
 *
 * The container starts once per JVM and is reused; [reset] truncates between
 * tests. Where Docker is unavailable the tests skip (see [available]) rather
 * than fail — a laptop without Docker should still be able to run `check`.
 */
object PostgresFixture {

    val available: Boolean by lazy {
        try {
            DockerClientFactory.instance().isDockerAvailable
        } catch (e: Throwable) {
            false
        }
    }

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:17") // same major as docker-compose.yml
            .withDatabaseName("airadar")
            .withUsername("airadar")
            .withPassword("airadar")
            .also { it.start() }
    }

    val dataSource: DataSource by lazy {
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = container.jdbcUrl
                username = container.username
                password = container.password
                poolName = "bookshelf-echo-test"
                maximumPoolSize = 2
            },
        )
        migrate(ds)
        ds
    }

    val repo: ItemRepository by lazy { ItemRepository(dataSource) }

    /**
     * Applies `db/migrations/V*.sql` in version order — the same files the
     * flyway container runs in compose, so the schema under test is the schema
     * that ships. Each file is executed as one statement batch; these
     * migrations contain no procedural bodies, so splitting on `;` is not
     * needed and JDBC handles the multi-statement string.
     */
    private fun migrate(ds: DataSource) {
        val dir = findMigrationsDir()
        val files = dir.listFiles { f: File -> f.name.endsWith(".sql") }
            ?.sortedBy { it.name.substringBefore("__").removePrefix("V").toInt() }
            ?: error("no migrations in $dir")
        ds.connection.use { c ->
            files.forEach { f ->
                c.createStatement().use { st -> st.execute(f.readText()) }
            }
        }
    }

    /**
     * The module's working directory is `common/`, but Gradle and IDEs disagree
     * on that often enough that walking up to the repo root is more reliable
     * than a fixed `../db/migrations`.
     */
    private fun findMigrationsDir(): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            val candidate = File(d, "db/migrations")
            if (candidate.isDirectory) return candidate
            d = d.parentFile
        }
        error("db/migrations not found above ${File("").absolutePath}")
    }

    /** Empty every table, so tests do not have to care about each other. */
    fun reset() {
        dataSource.connection.use { c ->
            c.createStatement().use { st ->
                st.execute(
                    """
                    TRUNCATE items, item_contents, digests, matches, shortlist, selection_runs,
                             essays, llm_usage, publish_log, metrics_snapshots
                    RESTART IDENTITY CASCADE
                    """.trimIndent(),
                )
            }
        }
    }
}
