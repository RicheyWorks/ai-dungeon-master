package com.xai.dungeonmaster.plugin.builtin.llm;

/**
 * xAI (Grok) narrator — OpenAI-compatible Chat Completions. Config via env /
 * system properties: {@code XAI_API_KEY} (required), {@code XAI_MODEL} (default
 * {@code grok-2-latest}), {@code XAI_BASE_URL} (default
 * {@code https://api.x.ai/v1}).
 */
public final class XaiProvider extends OpenAiCompatibleProvider {

    public static final String ID = "xai";

    public XaiProvider() {
        this(env("XAI_API_KEY", null),
                env("XAI_MODEL", "grok-2-latest"),
                env("XAI_BASE_URL", "https://api.x.ai/v1"),
                new JdkHttpTransport());
    }

    public XaiProvider(String apiKey, String model, String baseUrl, HttpTransport transport) {
        super(ID, "xAI (" + model + ")", baseUrl, model, apiKey, true, transport);
    }
}
