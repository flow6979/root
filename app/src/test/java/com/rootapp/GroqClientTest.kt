package com.rootapp

import com.rootapp.ai.ChatMessage
import com.rootapp.ai.GroqClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GroqClientTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun client() = GroqClient(
        apiKey = "test-key",
        model = "llama-3.1-8b-instant",
        baseUrl = server.url("/v1/").toString(),
    )

    @Test fun `parses assistant content from a successful response`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"role":"assistant","content":"  Hey, I'm here.  "}}]}""",
            ),
        )
        val reply = client().complete(listOf(ChatMessage.user("hi")))
        assertEquals("Hey, I'm here.", reply) // trimmed

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"role\":\"user\""))
    }

    @Test fun `throws on non-2xx`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))
        try {
            runBlocking { client().complete(listOf(ChatMessage.user("hi"))) }
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test fun `blank api key fails fast`() {
        try {
            runBlocking {
                GroqClient(apiKey = "", baseUrl = server.url("/v1/").toString())
                    .complete(listOf(ChatMessage.user("hi")))
            }
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("GROQ_API_KEY"))
        }
    }
}
