package com.xai.dungeonmaster.plugin.builtin.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.LLMProvider.NarrativePrompt;
import com.xai.dungeonmaster.plugin.LLMProvider.NarrativeResponse;
import com.xai.dungeonmaster.plugin.LLMProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the keyed HTTP providers via an injected fake transport — no network,
 * no API keys. Covers request shaping, response parsing, error fallback, the
 * no-key DOWN path, and ServiceLoader/registry discovery + fallback.
 */
class KeyedLLMProviderTest {

    private final ObjectMapper json = new ObjectMapper();

    private NarrativePrompt prompt() {
        return new NarrativePrompt("kick down the door", "the drowned chapel", 200);
    }

    /** Records the last request and returns a canned response. */
    static final class FakeTransport implements HttpTransport {
        int calls;
        String lastUrl, lastBody;
        Map<String, String> lastHeaders;
        final int status;
        final String responseBody;

        FakeTransport(int status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public Result post(String url, Map<String, String> headers, String jsonBody) throws IOException {
            calls++;
            lastUrl = url;
            lastHeaders = headers;
            lastBody = jsonBody;
            return new Result(status, responseBody);
        }
    }

    @AfterEach
    void reset() {
        LLMProviderRegistry.clearForTests();
    }

    @Test
    void openAiParsesCompletionAndShapesRequest() throws Exception {
        FakeTransport t = new FakeTransport(200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"The door splinters inward.\"}}],"
                        + "\"usage\":{\"total_tokens\":42}}");
        OpenAiProvider p = new OpenAiProvider("sk-test", "gpt-4o-mini", "https://api.openai.com/v1", t);

        NarrativeResponse r = p.generate(prompt());

        assertFalse(r.wasFallback);
        assertEquals("The door splinters inward.", r.text);
        assertEquals(42, r.tokensUsed, "token count should come from the usage block");
        assertEquals(LLMProvider.HealthStatus.OK, p.health());
        // request shaping
        assertEquals("https://api.openai.com/v1/chat/completions", t.lastUrl);
        assertEquals("Bearer sk-test", t.lastHeaders.get("Authorization"));
        assertEquals("gpt-4o-mini", json.readTree(t.lastBody).get("model").asText());
        assertEquals("user", json.readTree(t.lastBody).get("messages").get(1).get("role").asText());
    }

    @Test
    void nonSuccessStatusFallsBackAndDegrades() {
        FakeTransport t = new FakeTransport(500, "upstream boom");
        OpenAiProvider p = new OpenAiProvider("sk-test", "gpt-4o-mini", "https://api.openai.com/v1", t);

        NarrativeResponse r = p.generate(prompt());
        assertTrue(r.wasFallback, "HTTP 500 must yield a fallback");
        assertFalse(r.text.isBlank());
        assertEquals(LLMProvider.HealthStatus.DEGRADED, p.health());
    }

    @Test
    void missingKeyReportsDownAndNeverCallsTransport() {
        FakeTransport t = new FakeTransport(200, "{}");
        OpenAiProvider p = new OpenAiProvider(null, "gpt-4o-mini", "https://api.openai.com/v1", t);

        assertEquals(LLMProvider.HealthStatus.DOWN, p.health(), "no key -> DOWN");
        NarrativeResponse r = p.generate(prompt());
        assertTrue(r.wasFallback);
        assertEquals(0, t.calls, "no network call should be made without a key");
    }

    @Test
    void malformedJsonFallsBack() {
        FakeTransport t = new FakeTransport(200, "this is not json");
        OpenAiProvider p = new OpenAiProvider("sk-test", "m", "https://api.openai.com/v1", t);
        assertTrue(p.generate(prompt()).wasFallback);
    }

    @Test
    void anthropicParsesMessagesAndSetsHeaders() throws Exception {
        FakeTransport t = new FakeTransport(200,
                "{\"content\":[{\"type\":\"text\",\"text\":\"Shadows recoil as you step through.\"}],"
                        + "\"usage\":{\"input_tokens\":10,\"output_tokens\":15}}");
        AnthropicProvider p = new AnthropicProvider("sk-ant", "claude-3-5-sonnet-latest", "https://api.anthropic.com", t);

        NarrativeResponse r = p.generate(prompt());
        assertFalse(r.wasFallback);
        assertEquals("Shadows recoil as you step through.", r.text);
        assertEquals(25, r.tokensUsed, "input+output tokens");
        assertEquals("https://api.anthropic.com/v1/messages", t.lastUrl);
        assertEquals("sk-ant", t.lastHeaders.get("x-api-key"));
        assertEquals("2023-06-01", t.lastHeaders.get("anthropic-version"));
        assertTrue(json.readTree(t.lastBody).has("system"), "Anthropic uses a top-level system field");
    }

    @Test
    void registryDiscoversKeyedProvidersAndFallsBackWhenSelectedIsDown() {
        LLMProviderRegistry.clearForTests();
        var ids = LLMProviderRegistry.registeredIds();
        assertTrue(ids.contains("openai") && ids.contains("xai")
                        && ids.contains("anthropic") && ids.contains("llama"),
                "ServiceLoader should discover all keyed providers: " + ids);

        // A keyed provider with no key is DOWN, so getActive() must fall back to the stub.
        LLMProviderRegistry.register(new OpenAiProvider(null, "m", "https://api.openai.com/v1",
                new FakeTransport(200, "{}")));
        assertTrue(LLMProviderRegistry.setActive("openai"));
        assertEquals("local-stub", LLMProviderRegistry.getActive().id(),
                "a DOWN keyed provider should fall back to the offline stub");
    }
}
