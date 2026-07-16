package com.xai.dungeonmaster.plugin;

/**
 * Abstraction over an LLM backend (OpenAI, Anthropic, xAI, local llama.cpp, etc).
 *
 * The dungeon-master narration is the single biggest design gap in the current
 * engine. This interface lets us swap providers per deployment, per campaign,
 * or per turn (e.g., free-tier players get a cheap/local model; Premium DM
 * unlocks the flagship).
 *
 * Implementations must be thread-safe. Each generate() call may run on a
 * different worker thread, and multiple sessions may share a single provider
 * instance.
 */
public interface LLMProvider extends Plugin {

    /**
     * Generate a narration for the given prompt.
     *
     * @param prompt non-null prompt describing the in-game situation
     * @return non-null response (may be a fallback string on error)
     */
    NarrativeResponse generate(NarrativePrompt prompt);

    /**
     * True if this provider can stream tokens. Streaming providers can plug
     * into the typed WebSocket envelope to push partial narration as it
     * arrives. Non-streaming providers return the full text in one shot.
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Streaming variant of {@link #generate}. Emits the narration in one or
     * more chunks via {@code onChunk} and returns the full aggregated response.
     * The default is non-streaming: it calls {@link #generate} and emits the
     * whole text as a single chunk. Streaming-capable providers override this.
     */
    default NarrativeResponse generateStreaming(NarrativePrompt prompt, java.util.function.Consumer<String> onChunk) {
        NarrativeResponse r = generate(prompt);
        if (onChunk != null && r.text != null && !r.text.isEmpty()) onChunk.accept(r.text);
        return r;
    }

    /**
     * Lightweight liveness check used by the cost-guardrail / fallback logic.
     */
    default HealthStatus health() {
        return HealthStatus.UNKNOWN;
    }

    enum HealthStatus {
        OK,        // last call succeeded recently
        DEGRADED,  // some recent calls failed but provider is still trying
        DOWN,      // disable until manual reset
        UNKNOWN    // no recent data
    }

    /**
     * Input to {@link #generate}. Carries the prompt plus the engine context
     * the provider may want to weave into the narration.
     */
    final class NarrativePrompt {
        public final String userPrompt;
        public final String sceneContext;
        public final int maxTokens;

        /**
         * Compact, deterministic story facts from the engine's Chronicle
         * (ADR-001 Phase 3) — "Quest completed: The Weeping Tree", "Boss
         * slain: Grave Warden". Never null; empty when no memory is
         * available. Providers weave these into their context so narration
         * has continuity; the offline stub renders them as a recap.
         */
        public final java.util.List<String> contextFacts;

        public NarrativePrompt(String userPrompt, String sceneContext, int maxTokens) {
            this(userPrompt, sceneContext, maxTokens, null);
        }

        public NarrativePrompt(String userPrompt, String sceneContext, int maxTokens,
                               java.util.List<String> contextFacts) {
            this.userPrompt = userPrompt;
            this.sceneContext = sceneContext;
            this.maxTokens = Math.max(1, maxTokens);
            this.contextFacts = (contextFacts != null)
                    ? java.util.List.copyOf(contextFacts) : java.util.List.of();
        }
    }

    /**
     * Output from {@link #generate}. The {@code costEstimateUsd} field feeds
     * the cost-guardrail logic — providers that can't estimate may return 0.0.
     */
    final class NarrativeResponse {
        public final String text;
        public final int tokensUsed;
        public final double costEstimateUsd;
        public final boolean wasFallback;

        public NarrativeResponse(String text, int tokensUsed, double costEstimateUsd, boolean wasFallback) {
            this.text = text != null ? text : "";
            this.tokensUsed = Math.max(0, tokensUsed);
            this.costEstimateUsd = Math.max(0.0, costEstimateUsd);
            this.wasFallback = wasFallback;
        }
    }
}
