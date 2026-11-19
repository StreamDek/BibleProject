package app.bible.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

class Database private constructor(val dataSource: DataSource) {
    fun ping(): Boolean = runCatching {
        dataSource.connection.use { it.isValid(2) }
    }.getOrDefault(false)

    companion object {
        /** Connects, then applies pending migrations from resources/db/migration. */
        fun connect(config: AppConfig): Database {
            val ds = HikariDataSource(HikariConfig().apply {
                jdbcUrl = config.databaseUrl
                username = config.databaseUser
                password = config.databasePassword
                maximumPoolSize = config.databasePoolSize
                poolName = "bible-api"
            })
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            return Database(ds)
        }
    }
}
