package app.bible.backend

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val config = AppConfig.fromEnv()
    val database = config.databaseUrl?.let { Database.connect(config) }
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config, database)
    }.start(wait = true)
}

@Serializable
data class Health(val status: String, val version: String, val database: String)

@Serializable
data class ErrorBody(val error: String)

/**
 * The API is deliberately small: reading never depends on it (Scripture and content ship as static packs on
 * the CDN). It serves accounts, sync and the admin platform. Auth and sync routes arrive in Phase 7.
 */
fun Application.module(config: AppConfig, database: Database?) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    install(CallLogging) { level = Level.INFO }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@module.environment.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorBody("internal_error"))
        }
    }
    routing {
        get("/health") {
            val db = when {
                database == null -> "not_configured"
                database.ping() -> "ok"
                else -> "unreachable"
            }
            val status = if (db == "unreachable") HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK
            call.respond(status, Health(status = if (status == HttpStatusCode.OK) "ok" else "degraded", version = config.version, database = db))
        }
    }
}
