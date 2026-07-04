package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.LLMProvider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cost guardrail decorator: wraps any {@link LLMProvider} and enforces a
 * cumulative per-session token ceiling. Once further spend would exceed the
 * ceiling it stops calling the (potentially paid) delegate and returns a
 * deterministic fallback narration flagged {@code wasFallback=true}.
 *
 * This is exactly the decorator shape the roadmap calls for — a moderation
 * pre-filter ({@link ModerationProvider}) is another decorator of the same kind,
 * and the two compose: {@code new TokenBudgetProvider(new ModerationProvider(p), n)}.
 */
public final class TokenBudgetProvider implements LLMProvider {

    private final LLMProvider delegate;
    private final int sessionCeiling;
    private final AtomicInteger spent = new AtomicInteger(0);

    public TokenBudgetProvider(LLMProvider delegate, int sessionCeiling) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        this.delegate = delegate;
        this.sessionCeiling = Math.max(0, sessionCeiling);
    }

    @Override public String id() { return delegate.id(); }
    @Override public String displayName() { return delegate.displayName() + " (budgeted)"; }
    @Override public boolean supportsStreaming() { return delegate.supportsStreaming(); }
    @Override public HealthStatus health() { return delegate.health(); }

    public int tokensSpent() { return spent.get(); }
    public int sessionCeiling() { return sessionCeiling; }

    @Override
    public NarrativeResponse generate(NarrativePrompt prompt) {
        if (spent.get() >= sessionCeiling) {
            return new NarrativeResponse(
                    "The Dungeon Master pauses to catch their breath. (Narration budget reached.)",
                    0, 0.0, true);
        }
        NarrativeResponse r = delegate.generate(prompt);
        spent.addAndGet(r.tokensUsed);
        return r;
    }

    @Override
    public NarrativeResponse generateStreaming(NarrativePrompt prompt, java.util.function.Consumer<String> onChunk) {
        if (spent.get() >= sessionCeiling) {
            NarrativeResponse fb = new NarrativeResponse(
                    "The Dungeon Master pauses to catch their breath. (Narration budget reached.)",
                    0, 0.0, true);
            if (onChunk != null) onChunk.accept(fb.text);
            return fb;
        }
        NarrativeResponse r = delegate.generateStreaming(prompt, onChunk);
        spent.addAndGet(r.tokensUsed);
        return r;
    }
}
