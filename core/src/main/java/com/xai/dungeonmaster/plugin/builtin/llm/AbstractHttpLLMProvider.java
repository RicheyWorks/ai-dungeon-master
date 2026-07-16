package com.xai.dungeonmaster.plugin.builtin.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.plugin.LLMProvider;

import java.util.Map;

/**
 * Shared machinery for HTTP-backed, keyed narration providers (OpenAI, xAI,
 * Anthropic, local llama servers). Subclasses supply the endpoint, headers,
 * request body, and response parsing for their specific API; this base handles
 * key/health gating, error-to-fallback, and token estimation.
 *
 * Health model: a provider that requires a key but has none reports
 * {@link HealthStatus#DOWN}, so {@code LLMProviderRegistry.getActive()}
 * transparently falls back to the offline stub. A failed call degrades health;
 * a successful call restores it.
 */
public abstract class AbstractHttpLLMProvider implements LLMProvider {

    protected final ObjectMapper mapper = new ObjectMapper();

    private final String id;
    private final String displayName;
    protected final String baseUrl;
    protected final String model;
    protected final String apiKey;
    protected final boolean keyRequired;
    protected final HttpTransport transport;

    private volatile HealthStatus health;

    protected AbstractHttpLLMProvider(String id, String displayName, String baseUrl, String model,
                                      String apiKey, boolean keyRequired, HttpTransport transport) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.model = model;
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey.trim();
        this.keyRequired = keyRequired;
        this.transport = (transport != null) ? transport : new JdkHttpTransport();
        this.health = (keyRequired && this.apiKey == null) ? HealthStatus.DOWN : HealthStatus.UNKNOWN;
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public HealthStatus health() { return health; }

    @Override
    public NarrativeResponse generate(NarrativePrompt prompt) {
        if (keyRequired && apiKey == null) {
            return fallback("no API key configured for " + id);
        }
        try {
            HttpTransport.Result res = transport.post(endpoint(), headers(), buildRequestBody(prompt));
            if (!res.isSuccess()) {
                health = HealthStatus.DEGRADED;
                return fallback("HTTP " + res.status());
            }
            String text = parseText(res.body());
            if (text == null || text.isBlank()) {
                health = HealthStatus.DEGRADED;
                return fallback("empty completion");
            }
            int tokens = parseTokens(res.body(), text);
            health = HealthStatus.OK;
            return new NarrativeResponse(text.trim(), tokens, estimateCost(tokens), false);
        } catch (Exception e) {
            health = HealthStatus.DEGRADED;
            return fallback(e.getClass().getSimpleName());
        }
    }

    /** A graceful, non-throwing fallback narration flagged {@code wasFallback=true}. */
    protected NarrativeResponse fallback(String reason) {
        String text = "The vision clouds (" + reason + "); the Dungeon Master carries the tale onward in a steadier voice.";
        return new NarrativeResponse(text, estimateTokens(text), 0.0, true);
    }

    /** Default system instruction woven around the scene context and story memory. */
    protected String systemPrompt(NarrativePrompt prompt) {
        String scene = (prompt != null && prompt.sceneContext != null) ? prompt.sceneContext.trim() : "";
        StringBuilder sb = new StringBuilder(
                "You are the Dungeon Master of a dark, multiversal dungeon crawler. "
                + "Narrate the outcome in 2-4 vivid, second-person sentences. Do not break character.");
        if (!scene.isEmpty()) {
            sb.append(" Current scene: ").append(scene).append('.');
        }
        // Narrative memory (ADR-001 Phase 3): compact facts from the engine's
        // Chronicle. Reference them for continuity; never contradict them.
        if (prompt != null && !prompt.contextFacts.isEmpty()) {
            sb.append(" What has happened so far: ")
              .append(String.join("; ", prompt.contextFacts))
              .append(". Stay consistent with these events.");
        }
        return sb.toString();
    }

    protected String userContent(NarrativePrompt prompt) {
        return (prompt != null && prompt.userPrompt != null) ? prompt.userPrompt.trim() : "";
    }

    // ── subclass hooks ──────────────────────────────────────────────────────
    protected abstract String endpoint();
    protected abstract Map<String, String> headers();
    protected abstract String buildRequestBody(NarrativePrompt prompt) throws Exception;
    protected abstract String parseText(String body) throws Exception;

    /** Token count from the provider's usage block, falling back to a word estimate. */
    protected int parseTokens(String body, String text) {
        return estimateTokens(text);
    }

    /** Rough USD estimate; providers may override. Default 0 (unknown). */
    protected double estimateCost(int tokens) {
        return 0.0;
    }

    // ── helpers ─────────────────────────────────────────────────────────────
    protected static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    /** Resolve config from an env var, then a JVM system property, then a default. */
    protected static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = System.getProperty(name);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
