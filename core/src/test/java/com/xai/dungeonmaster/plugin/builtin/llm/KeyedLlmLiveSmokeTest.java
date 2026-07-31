package com.xai.dungeonmaster.plugin.builtin.llm;

import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.LLMProvider.NarrativePrompt;
import com.xai.dungeonmaster.plugin.LLMProvider.NarrativeResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in live smoke against real keyed providers. Skipped in normal CI.
 *
 * Enable by setting:
 * <pre>
 *   LLM_LIVE_SMOKE=true
 *   OPENAI_API_KEY=…          # and/or XAI_API_KEY / ANTHROPIC_API_KEY
 * </pre>
 * then:
 * <pre>
 *   mvn -pl core -Dtest=KeyedLlmLiveSmokeTest test
 * </pre>
 *
 * Each provider test is independently gated on its own key so a partial
 * credential set still exercises what is available.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "LLM_LIVE_SMOKE", matches = "(?i)true|1|yes")
class KeyedLlmLiveSmokeTest {

    private NarrativePrompt prompt() {
        return new NarrativePrompt(
                "Describe a single torch flickering in a damp stone corridor.",
                "the Genesis Rift",
                80,
                java.util.List.of("Party entered the Genesis Rift"));
    }

    @Test
    void openAiLive() {
        String key = env("OPENAI_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "OPENAI_API_KEY not set — skip");
        OpenAiProvider p = new OpenAiProvider(key,
                envOr("OPENAI_MODEL", "gpt-4o-mini"),
                envOr("OPENAI_BASE_URL", "https://api.openai.com/v1"),
                new JdkHttpTransport());
        assertHealthyLive(p);
    }

    @Test
    void xaiLive() {
        String key = env("XAI_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "XAI_API_KEY not set — skip");
        XaiProvider p = new XaiProvider(key,
                envOr("XAI_MODEL", "grok-2-latest"),
                envOr("XAI_BASE_URL", "https://api.x.ai/v1"),
                new JdkHttpTransport());
        assertHealthyLive(p);
    }

    @Test
    void anthropicLive() {
        String key = env("ANTHROPIC_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "ANTHROPIC_API_KEY not set — skip");
        AnthropicProvider p = new AnthropicProvider(key,
                envOr("ANTHROPIC_MODEL", "claude-3-5-sonnet-latest"),
                envOr("ANTHROPIC_BASE_URL", "https://api.anthropic.com"),
                new JdkHttpTransport());
        assertHealthyLive(p);
    }

    private void assertHealthyLive(LLMProvider p) {
        NarrativeResponse r = p.generate(prompt());
        assertNotNull(r);
        assertFalse(r.wasFallback, "live call fell back: " + r.text);
        assertFalse(r.text.isBlank());
        assertEquals(LLMProvider.HealthStatus.OK, p.health());
        System.out.println("[live-smoke] " + p.id() + " → " + r.text.substring(0, Math.min(120, r.text.length())));
    }

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = System.getProperty(name);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String envOr(String name, String def) {
        String v = env(name);
        return v != null ? v : def;
    }
}
