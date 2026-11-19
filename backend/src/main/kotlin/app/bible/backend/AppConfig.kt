package app.bible.backend

/** Configuration from environment variables (set in deploy/.env on the VPS). */
data class AppConfig(
    val port: Int,
    val version: String,
    val databaseUrl: String?,
    val databaseUser: String,
    val databasePassword: String,
    val databasePoolSize: Int,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = AppConfig(
            port = env["PORT"]?.toInt() ?: 8080,
            version = env["APP_VERSION"] ?: "dev",
            databaseUrl = env["DATABASE_URL"],               // e.g. jdbc:postgresql://postgres:5432/bible
            databaseUser = env["DATABASE_USER"] ?: "bible",
            databasePassword = env["DATABASE_PASSWORD"] ?: "",
            // Small pool: the VPS has 2 GB RAM and Postgres is tuned for ~30 connections in total.
            databasePoolSize = env["DATABASE_POOL_SIZE"]?.toInt() ?: 8,
        )
    }
}
