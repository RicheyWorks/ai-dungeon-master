package com.xai.dungeonmaster.plugin.builtin.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider for any OpenAI-compatible Chat Completions endpoint
 * ({@code POST {baseUrl}/chat/completions}). OpenAI, xAI (Grok), and most local
 * servers (llama.cpp, LM Studio, vLLM) speak this schema, so they differ only in
 * base URL, model, and key — see the thin subclasses.
 */
public class OpenAiCompatibleProvider extends AbstractHttpLLMProvider {

    public OpenAiCompatibleProvider(String id, String displayName, String baseUrl, String model,
                                    String apiKey, boolean keyRequired, HttpTransport transport) {
        super(id, displayName, baseUrl, model, apiKey, keyRequired, transport);
    }

    @Override
    protected String endpoint() {
        return baseUrl + "/chat/completions";
    }

    @Override
    protected Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        if (apiKey != null) {
            h.put("Authorization", "Bearer " + apiKey);
        }
        return h;
    }

    @Override
    protected String buildRequestBody(NarrativePrompt prompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", prompt != null ? prompt.maxTokens : 256);
        root.put("temperature", 0.8);
        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt(prompt));
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", userContent(prompt));
        return mapper.writeValueAsString(root);
    }

    @Override
    protected String parseText(String body) throws Exception {
        JsonNode choices = mapper.readTree(body).path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText(null);
        }
        return null;
    }

    @Override
    protected int parseTokens(String body, String text) {
        try {
            JsonNode usage = mapper.readTree(body).path("usage");
            if (usage.has("total_tokens")) {
                return usage.get("total_tokens").asInt();
            }
        } catch (Exception ignored) {
            // fall through to estimate
        }
        return estimateTokens(text);
    }
}
