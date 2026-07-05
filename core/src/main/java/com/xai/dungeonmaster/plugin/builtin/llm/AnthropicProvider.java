package com.xai.dungeonmaster.plugin.builtin.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic Messages API narrator ({@code POST {baseUrl}/v1/messages}). Config
 * via env / system properties: {@code ANTHROPIC_API_KEY} (required),
 * {@code ANTHROPIC_MODEL} (default {@code claude-3-5-sonnet-latest}),
 * {@code ANTHROPIC_BASE_URL} (default {@code https://api.anthropic.com}),
 * {@code ANTHROPIC_VERSION} (default {@code 2023-06-01}).
 */
public final class AnthropicProvider extends AbstractHttpLLMProvider {

    public static final String ID = "anthropic";

    private final String anthropicVersion;

    public AnthropicProvider() {
        this(env("ANTHROPIC_API_KEY", null),
                env("ANTHROPIC_MODEL", "claude-3-5-sonnet-latest"),
                env("ANTHROPIC_BASE_URL", "https://api.anthropic.com"),
                new JdkHttpTransport());
    }

    public AnthropicProvider(String apiKey, String model, String baseUrl, HttpTransport transport) {
        super(ID, "Anthropic (" + model + ")", baseUrl, model, apiKey, true, transport);
        this.anthropicVersion = env("ANTHROPIC_VERSION", "2023-06-01");
    }

    @Override
    protected String endpoint() {
        return baseUrl + "/v1/messages";
    }

    @Override
    protected Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        h.put("anthropic-version", anthropicVersion);
        if (apiKey != null) {
            h.put("x-api-key", apiKey);
        }
        return h;
    }

    @Override
    protected String buildRequestBody(NarrativePrompt prompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", prompt != null ? prompt.maxTokens : 256);
        root.put("system", systemPrompt(prompt));
        ArrayNode messages = root.putArray("messages");
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", userContent(prompt));
        return mapper.writeValueAsString(root);
    }

    @Override
    protected String parseText(String body) throws Exception {
        JsonNode content = mapper.readTree(body).path("content");
        if (content.isArray() && !content.isEmpty()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText()) || block.has("text")) {
                    return block.path("text").asText(null);
                }
            }
        }
        return null;
    }

    @Override
    protected int parseTokens(String body, String text) {
        try {
            JsonNode u = mapper.readTree(body).path("usage");
            int in = u.path("input_tokens").asInt(0);
            int out = u.path("output_tokens").asInt(0);
            if (in + out > 0) {
                return in + out;
            }
        } catch (Exception ignored) {
            // fall through to estimate
        }
        return estimateTokens(text);
    }
}
