package wiki.nplus.airadar.common

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object Db {
    fun dataSource(appName: String): DataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = Config.str(
                "DATABASE_URL",
                "jdbc:postgresql://127.0.0.1:${Config.int("POSTGRES_PORT", 5432)}/${Config.str("POSTGRES_DB", "airadar")}",
            )
            username = Config.str("POSTGRES_USER", "airadar")
            password = Config.str("POSTGRES_PASSWORD")
            poolName = "bookshelf-echo-$appName"
            maximumPoolSize = 4
        },
    )
}
