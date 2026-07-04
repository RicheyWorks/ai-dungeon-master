package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.LLMProvider;

/**
 * Offline, deterministic dungeon-master narrator.
 *
 * Needs no API key and makes no network calls, so it works in tests, in CI, and
 * as the free-tier / privacy ("local") narration mode described in the roadmap.
 * Given the same prompt it always returns the same text, which keeps API
 * snapshots and unit tests stable. Real keyed providers (OpenAI, Anthropic,
 * xAI, llama.cpp) implement this same interface and slot in behind the registry.
 */
public final class LocalStubProvider implements LLMProvider {

    /** Stable, lowercase-kebab id (Plugin convention for external-style ids). */
    public static final String ID = "local-stub";

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Local Stub Narrator (offline)"; }
    @Override public HealthStatus health() { return HealthStatus.OK; }

    @Override
    public NarrativeResponse generate(NarrativePrompt prompt) {
        String user = (prompt != null && prompt.userPrompt != null) ? prompt.userPrompt.trim() : "";
        String scene = (prompt != null && prompt.sceneContext != null) ? prompt.sceneContext.trim() : "";
        int cap = (prompt != null) ? prompt.maxTokens : 256;

        StringBuilder sb = new StringBuilder("The Dungeon Master weaves the threads of fate. ");
        if (!scene.isEmpty()) {
            sb.append("Around you lies ").append(scene).append(". ");
        }
        if (!user.isEmpty()) {
            sb.append("As you ").append(lowerFirst(user)).append(", the rift answers in kind.");
        } else {
            sb.append("The rift hums, waiting for your next move.");
        }

        String text = clampByWords(sb.toString(), cap);
        return new NarrativeResponse(text, estimateTokens(text), 0.0, false);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Rough token estimate: ~1 token per whitespace-delimited word. */
    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    /** Clamp to an approximate token (word) budget so maxTokens is honored. */
    static String clampByWords(String text, int maxTokens) {
        if (maxTokens <= 0) return "";
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxTokens) return text.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxTokens; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }
}
