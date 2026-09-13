package snd.komelia.ui.login

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import snd.komelia.offline.user.model.OfflineUser
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginHttpRetryTest {
    @Test fun onlyGatewayFailuresRetryAndUnauthorizedForbiddenAndBadRequestDoNot() = runBlocking {
        for (status in listOf(401, 403, 400, 500, 502, 503, 504)) {
            val calls = AtomicInteger()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    calls.incrementAndGet()
                    exchange.sendResponseHeaders(status, -1)
                    exchange.close()
                }
                start()
            }
            val client = HttpClient(OkHttp) { expectSuccess = true }
            try {
                val login = OnlineLoginCoordinator(LoginTestResolver(), {
                    client.get("http://127.0.0.1:${server.address.port}/")
                    OfflineUser.ROOT_USER.toKomgaUser()
                }, { emptyList() })
                val failure = runCatching { login.login("http://primary.invalid") }.exceptionOrNull()
                assertTrue(failure is ResponseException)
                assertEquals(if (status in 502..504) 2 else 1, calls.get(), "HTTP $status")
            } finally { client.close(); server.stop(0) }
        }
    }
}
