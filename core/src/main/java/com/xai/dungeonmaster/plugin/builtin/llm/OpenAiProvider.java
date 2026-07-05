package com.xai.dungeonmaster.plugin.builtin.llm;

/**
 * OpenAI Chat Completions narrator. Config via env / system properties:
 * {@code OPENAI_API_KEY} (required), {@code OPENAI_MODEL} (default
 * {@code gpt-4o-mini}), {@code OPENAI_BASE_URL} (default
 * {@code https://api.openai.com/v1}). Reports DOWN — and the registry falls back
 * to the offline stub — when no key is set.
 */
public final class OpenAiProvider extends OpenAiCompatibleProvider {

    public static final String ID = "openai";

    /** ServiceLoader entry point: resolves config from the environment. */
    public OpenAiProvider() {
        this(env("OPENAI_API_KEY", null),
                env("OPENAI_MODEL", "gpt-4o-mini"),
                env("OPENAI_BASE_URL", "https://api.openai.com/v1"),
                new JdkHttpTransport());
    }

    public OpenAiProvider(String apiKey, String model, String baseUrl, HttpTransport transport) {
        super(ID, "OpenAI (" + model + ")", baseUrl, model, apiKey, true, transport);
    }
}
