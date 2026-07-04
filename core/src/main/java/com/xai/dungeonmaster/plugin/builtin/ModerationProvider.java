package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.LLMProvider;

import java.util.List;
import java.util.Locale;

/**
 * Moderation filter implemented as an {@link LLMProvider} decorator, exactly as
 * the roadmap describes: "a moderation pre-filter is itself an LLMProvider
 * decorator so it can be slotted in front of any backend."
 *
 * This minimal version redacts a small blocklist from generated narration. A
 * production deployment would swap in a hosted moderation model behind the same
 * interface without touching any caller. The blocklist here uses placeholder
 * tokens ("slur1"/"slur2") rather than real terms.
 */
public final class ModerationProvider implements LLMProvider {

    private static final List<String> BLOCKLIST = List.of("slur1", "slur2");
    private static final String REDACTION = "[redacted]";

    private final LLMProvider delegate;

    public ModerationProvider(LLMProvider delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        this.delegate = delegate;
    }

    @Override public String id() { return delegate.id(); }
    @Override public String displayName() { return delegate.displayName() + " (moderated)"; }
    @Override public boolean supportsStreaming() { return delegate.supportsStreaming(); }
    @Override public HealthStatus health() { return delegate.health(); }

    @Override
    public NarrativeResponse generate(NarrativePrompt prompt) {
        NarrativeResponse r = delegate.generate(prompt);
        String cleaned = redact(r.text);
        if (cleaned.equals(r.text)) return r;
        return new NarrativeResponse(cleaned, r.tokensUsed, r.costEstimateUsd, r.wasFallback);
    }

    /** Case-insensitive redaction of every blocklisted term. */
    static String redact(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String out = text;
        for (String bad : BLOCKLIST) {
            int from = 0;
            String lower = out.toLowerCase(Locale.ROOT);
            int idx;
            while ((idx = lower.indexOf(bad, from)) >= 0) {
                out = out.substring(0, idx) + REDACTION + out.substring(idx + bad.length());
                lower = out.toLowerCase(Locale.ROOT);
                from = idx + REDACTION.length();
            }
        }
        return out;
    }
}
