package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.plugin.builtin.LocalStubProvider;
import com.xai.dungeonmaster.plugin.builtin.ModerationProvider;
import com.xai.dungeonmaster.plugin.builtin.TokenBudgetProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the offline LLM slice: the deterministic stub provider, registry
 * discovery + fallback, the token-budget and moderation decorators, and the
 * engine's narrate() path — all without any API key or network access.
 */
class LlmProviderTest {

    private LLMProvider.NarrativePrompt prompt(String user) {
        return new LLMProvider.NarrativePrompt(user, "the Genesis Rift", 256);
    }

    @Test
    void localStubIsDeterministicAndOffline() {
        LocalStubProvider p = new LocalStubProvider();
        LLMProvider.NarrativeResponse a = p.generate(prompt("open the door"));
        LLMProvider.NarrativeResponse b = p.generate(prompt("open the door"));
        assertEquals(a.text, b.text, "same prompt -> same text (deterministic)");
        assertFalse(a.text.isBlank());
        assertTrue(a.tokensUsed > 0);
        assertEquals(LLMProvider.HealthStatus.OK, p.health());
    }

    @Test
    void registryDiscoversStubViaServiceLoaderAndActiveIsNonNull() {
        LLMProviderRegistry.clearForTests();
        assertTrue(LLMProviderRegistry.registeredIds().contains(LocalStubProvider.ID),
                "ServiceLoader should discover the bundled stub provider");
        LLMProvider active = LLMProviderRegistry.getActive();
        assertNotNull(active);
        assertEquals(LocalStubProvider.ID, active.id());
    }

    @Test
    void tokenBudgetFallsBackWhenCeilingExceeded() {
        TokenBudgetProvider budgeted = new TokenBudgetProvider(new LocalStubProvider(), 0);
        LLMProvider.NarrativeResponse r = budgeted.generate(prompt("charge"));
        assertTrue(r.wasFallback, "zero budget must yield a fallback response");
    }

    @Test
    void tokenBudgetAllowsCallsUnderCeilingAndTracksSpend() {
        TokenBudgetProvider budgeted = new TokenBudgetProvider(new LocalStubProvider(), 10_000);
        LLMProvider.NarrativeResponse r = budgeted.generate(prompt("charge"));
        assertFalse(r.wasFallback);
        assertTrue(budgeted.tokensSpent() > 0);
    }

    @Test
    void moderationRedactsBlocklistedTerms() {
        LLMProvider raw = new LLMProvider() {
            @Override public String id() { return "test-raw"; }
            @Override public NarrativeResponse generate(NarrativePrompt p) {
                return new NarrativeResponse("hello slur1 world", 3, 0.0, false);
            }
        };
        ModerationProvider mod = new ModerationProvider(raw);
        LLMProvider.NarrativeResponse r = mod.generate(prompt("x"));
        assertFalse(r.text.toLowerCase().contains("slur1"), "blocked term must be gone");
        assertTrue(r.text.contains("[redacted]"));
    }

    @Test
    void engineNarrateProducesTextThroughProvider() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3, new String[] { "Kael" }, new String[] { "Warrior" });
        LLMProvider.NarrativeResponse r = engine.narrate("look around");
        assertNotNull(r);
        assertFalse(r.text.isBlank(), "narration text should not be blank");
    }

    @Test
    void localStubStreamsMultipleChunks() {
        LocalStubProvider p = new LocalStubProvider();
        assertTrue(p.supportsStreaming());
        StringBuilder sb = new StringBuilder();
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        LLMProvider.NarrativeResponse r = p.generateStreaming(prompt("advance"),
                c -> { sb.append(c); n.incrementAndGet(); });
        assertTrue(n.get() > 1, "streaming should emit multiple chunks");
        assertEquals(r.text.replaceAll("\\s+", ""), sb.toString().replaceAll("\\s+", ""),
                "concatenated chunks reconstruct the full text");
    }
}
