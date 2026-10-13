package com.pft.financetracker

import com.pft.financetracker.domain.ai.AnthropicProvider
import com.pft.financetracker.domain.ai.BedrockProvider
import com.pft.financetracker.domain.ai.HttpJson
import com.pft.financetracker.domain.ai.JsonText
import com.pft.financetracker.domain.ai.LlmRequest
import com.pft.financetracker.domain.ai.LlmResult
import com.pft.financetracker.domain.ai.OpenAiCompatibleProvider
import com.pft.financetracker.domain.ai.ProviderConfig
import com.pft.financetracker.domain.ai.ProviderKind
import com.pft.financetracker.domain.ai.Providers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Each adapter against a fake server on this machine: what it sends, what it reads back, and that no key leaks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LlmProvidersTest {
    private class Seen(val path: String, val headers: Map<String, String>, val body: JSONObject)

    private lateinit var server: ServerSocket
    private val seen = java.util.Collections.synchronizedList(mutableListOf<Seen>())
    /** Replies in order; the last one repeats. */
    @Volatile private var replies = listOf(200 to "{}")
    private val base get() = "http://localhost:${server.localPort}"

    /** A minimal HTTP/1.1 server (the JDK's HttpServer isn't on the Android test classpath): one request per connection. */
    @Before fun start() {
        server = ServerSocket(0, 50, InetAddress.getByName("localhost"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { s ->
                    val input = s.getInputStream()
                    fun line(): String {
                        val b = StringBuilder()
                        while (true) {
                            val c = input.read()
                            if (c < 0 || c == '\n'.code) break
                            if (c != '\r'.code) b.append(c.toChar())
                        }
                        return b.toString()
                    }
                    val path = line().split(" ").getOrElse(1) { "/" }
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val h = line()
                        if (h.isEmpty()) break
                        val i = h.indexOf(':')
                        if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
                    }
                    val length = headers["content-length"]?.toInt() ?: 0
                    val body = ByteArray(length)
                    var read = 0
                    while (read < length) { val n = input.read(body, read, length - read); if (n < 0) break; read += n }
                    seen += Seen(path, headers, JSONObject(String(body, Charsets.UTF_8)))
                    val (code, text) = replies[minOf(seen.size - 1, replies.size - 1)]
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    val out = s.getOutputStream()
                    out.write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    @After fun stop() = server.close()

    private val request = LlmRequest("Be brief.", listOf(LlmRequest.Turn(LlmRequest.Role.USER, "Hi"), LlmRequest.Turn(LlmRequest.Role.ASSISTANT, "Hello"), LlmRequest.Turn(LlmRequest.Role.USER, "Food spend?")))

    @Test fun openAiCompatibleSendsChatCompletions() = runBlocking {
        replies = listOf(200 to """{"choices":[{"message":{"role":"assistant","content":" ₹2,400 on food. "}}]}""")
        val r = OpenAiCompatibleProvider(ProviderKind.GROQ, "$base/openai/v1", "llama-3.3-70b-versatile", "gsk_testkey123456789").complete(request)
        assertEquals(LlmResult.Ok("₹2,400 on food."), r)
        val s = seen.single()
        assertEquals("/openai/v1/chat/completions", s.path)
        assertEquals("Bearer gsk_testkey123456789", s.headers["authorization"])
        assertEquals("llama-3.3-70b-versatile", s.body.getString("model"))
        val msgs = s.body.getJSONArray("messages")
        assertEquals(4, msgs.length())
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        assertEquals("assistant", msgs.getJSONObject(2).getString("role"))
        assertFalse(s.body.has("response_format"))
    }

    @Test fun aLocalServerWithoutAKeySendsNoAuthorization() = runBlocking {
        replies = listOf(200 to """{"choices":[{"message":{"content":"ok"}}]}""")
        OpenAiCompatibleProvider(ProviderKind.LOCAL, "$base/v1", "llama3.2", null).complete(request)
        assertNull(seen.single().headers["authorization"])
    }

    @Test fun jsonModeIsRetriedWithoutResponseFormatWhenAServerRejectsIt() = runBlocking {
        replies = listOf(400 to """{"error":{"message":"response_format is not supported"}}""", 200 to """{"choices":[{"message":{"content":"Sure: {\"a\": 1} done"}}]}""")
        val r = OpenAiCompatibleProvider(ProviderKind.CUSTOM, "$base/v1", "m", "k").complete(request.copy(wantJson = true))
        assertEquals(LlmResult.Ok("""{"a": 1}"""), r)
        assertTrue(seen[0].body.has("response_format"))
        assertFalse(seen[1].body.has("response_format"))
    }

    @Test fun anthropicSendsMessagesWithTheSystemApart() = runBlocking {
        replies = listOf(200 to """{"content":[{"type":"text","text":"Claude says hi"}]}""")
        val r = AnthropicProvider("$base/v1", "claude-haiku-5-5", "sk-ant-secret-0123456789").complete(request)
        assertEquals(LlmResult.Ok("Claude says hi"), r)
        val s = seen.single()
        assertEquals("/v1/messages", s.path)
        assertEquals("sk-ant-secret-0123456789", s.headers["x-api-key"])
        assertEquals("2023-06-01", s.headers["anthropic-version"])
        assertEquals("Be brief.", s.body.getString("system"))
        assertEquals(3, s.body.getJSONArray("messages").length())
    }

    @Test fun bedrockSendsConverseWithTheModelInThePath() = runBlocking {
        replies = listOf(200 to """{"output":{"message":{"role":"assistant","content":[{"text":"{\"ok\":true}"}]}}}""")
        val r = BedrockProvider(base, "us.meta.llama3-3-70b-instruct-v1:0", "ABSKtestbedrockkey0000").complete(request.copy(wantJson = true))
        assertEquals(LlmResult.Ok("""{"ok":true}"""), r)
        val s = seen.single()
        assertEquals("/model/us.meta.llama3-3-70b-instruct-v1%3A0/converse", s.path)
        assertEquals("Bearer ABSKtestbedrockkey0000", s.headers["authorization"])
        assertTrue(s.body.getJSONArray("system").getJSONObject(0).getString("text").contains("JSON"))
        assertEquals(1_200, s.body.getJSONObject("inferenceConfig").getInt("maxTokens"))
    }

    @Test fun anErrorNeverShowsTheKey() = runBlocking {
        replies = listOf(401 to """{"error":{"message":"Incorrect API key provided: sk-proj-abcdefghijklmnop1234"}}""")
        val r = OpenAiCompatibleProvider(ProviderKind.OPENAI, "$base/v1", "gpt-4o-mini", "sk-proj-abcdefghijklmnop1234").complete(request)
        val msg = (r as LlmResult.Error).message
        assertFalse(msg.contains("abcdefghijklmnop"))
        assertTrue(msg.startsWith("Incorrect API key"))
    }

    @Test fun plainHttpOnlyGoesToThisPhone() {
        assertTrue(HttpJson.allowed("https://api.groq.com/openai/v1"))
        assertTrue(HttpJson.allowed("http://localhost:11434/v1"))
        assertTrue(HttpJson.allowed("http://127.0.0.1:1234/v1"))
        assertFalse(HttpJson.allowed("http://192.168.1.20:11434/v1"))
        assertFalse(HttpJson.allowed("http://example.com/v1"))
        assertFalse(HttpJson.allowed("ftp://localhost/v1"))
        assertFalse(HttpJson.allowed("https://"))
    }

    @Test fun aProviderIsBuiltOnlyWhenItIsSetUp() {
        assertNull(Providers.build(ProviderConfig(ProviderKind.ANTHROPIC)))
        assertTrue(Providers.build(ProviderConfig(ProviderKind.ANTHROPIC, apiKey = "sk-ant-x")) is AnthropicProvider)
        assertTrue(Providers.build(ProviderConfig(ProviderKind.LOCAL)) is OpenAiCompatibleProvider)
        assertNull(Providers.build(ProviderConfig(ProviderKind.CUSTOM)))
        assertNull(Providers.build(ProviderConfig(ProviderKind.LOCAL, baseUrl = "http://192.168.1.20:11434/v1")))
        assertEquals("https://bedrock-runtime.ap-south-1.amazonaws.com", ProviderConfig(ProviderKind.BEDROCK, region = "ap-south-1").resolvedBaseUrl)
    }

    @Test fun theOpenAiDefaultKeepsItsOldCacheKey() {
        assertEquals("", Providers.build(ProviderConfig(ProviderKind.OPENAI, apiKey = "sk-x"))!!.cacheScope)
        assertEquals("openai:gpt-4.1-mini", Providers.build(ProviderConfig(ProviderKind.OPENAI, model = "gpt-4.1-mini", apiKey = "sk-x"))!!.cacheScope)
    }

    @Test fun jsonIsFoundInsideProseAndFences() {
        assertEquals("""{"a":{"b":"}"}}""", JsonText.firstObject("Here you go:\n```json\n{\"a\":{\"b\":\"}\"}}\n```"))
        assertNull(JsonText.firstObject("no json here"))
    }
}
