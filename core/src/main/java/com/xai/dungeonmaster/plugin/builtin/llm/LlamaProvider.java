package com.xai.dungeonmaster.plugin.builtin.llm;

/**
 * Local llama.cpp / LM Studio / vLLM narrator — OpenAI-compatible Chat
 * Completions served locally. Config via env / system properties:
 * {@code LLAMA_BASE_URL} (default {@code http://localhost:8080/v1}),
 * {@code LLAMA_MODEL} (default {@code local-model}), {@code LLAMA_API_KEY}
 * (optional — most local servers need none). This is the privacy / offline
 * "bring your own GPU" narration mode.
 */
public final class LlamaProvider extends OpenAiCompatibleProvider {

    public static final String ID = "llama";

    public LlamaProvider() {
        this(env("LLAMA_API_KEY", null),
                env("LLAMA_MODEL", "local-model"),
                env("LLAMA_BASE_URL", "http://localhost:8080/v1"),
                new JdkHttpTransport());
    }

    public LlamaProvider(String apiKey, String model, String baseUrl, HttpTransport transport) {
        // Key not required: local servers are typically unauthenticated.
        super(ID, "Local llama (" + model + ")", baseUrl, model, apiKey, false, transport);
    }
}
