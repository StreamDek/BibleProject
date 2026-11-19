package app.bible.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthTest {
    @Test fun healthWithoutDatabase() = testApplication {
        application { module(AppConfig.fromEnv(emptyMap()), database = null) }
        val res = client.get("/health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue("\"database\":\"not_configured\"" in res.bodyAsText())
    }
}
