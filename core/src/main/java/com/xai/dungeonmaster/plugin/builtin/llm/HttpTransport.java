package com.xai.dungeonmaster.plugin.builtin.llm;

import java.io.IOException;
import java.util.Map;

/**
 * Minimal HTTP seam for keyed {@link com.xai.dungeonmaster.plugin.LLMProvider}
 * implementations. Abstracting the transport keeps the providers unit-testable:
 * production uses {@link JdkHttpTransport}, tests inject a fake that returns
 * canned responses so no network (or API key) is ever required.
 */
public interface HttpTransport {

    /** POST a JSON body and return the status + response body. */
    Result post(String url, Map<String, String> headers, String jsonBody) throws IOException;

    /** HTTP status code plus the raw response body. */
    record Result(int status, String body) {
        public boolean isSuccess() {
            return status / 100 == 2;
        }
    }
}
