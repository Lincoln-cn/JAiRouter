package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UrlQueryRedactor} 单元测试 —— issue #695：日志 / span 里的 URL 不得携带凭据。
 */
@DisplayName("UrlQueryRedactor URL query 脱敏")
class UrlQueryRedactorTest {

    @Test
    @DisplayName("token / access_token / api_key / apiKey 的值全部被抹掉，键名保留")
    void redactsEveryCredentialQueryParameter() {
        String url = "http://localhost:8080/api/models?token=jwt-secret&access_token=oauth-secret"
                + "&api_key=sk-live-secret&apiKey=sk-camel-secret&model=gpt-4o";

        String redacted = UrlQueryRedactor.redact(url);

        assertFalse(redacted.contains("jwt-secret"), redacted);
        assertFalse(redacted.contains("oauth-secret"), redacted);
        assertFalse(redacted.contains("sk-live-secret"), redacted);
        assertFalse(redacted.contains("sk-camel-secret"), redacted);
        assertTrue(redacted.contains("token=[REDACTED]"), redacted);
        assertTrue(redacted.contains("access_token=[REDACTED]"), redacted);
        assertTrue(redacted.contains("api_key=[REDACTED]"), redacted);
        assertTrue(redacted.contains("apiKey=[REDACTED]"), redacted);
        // 非敏感参数与非 query 部分必须保持不变，日志仍然可读
        assertTrue(redacted.startsWith("http://localhost:8080/api/models?"), redacted);
        assertTrue(redacted.contains("model=gpt-4o"), redacted);
    }

    @Test
    @DisplayName("无 query、null、空串原样返回")
    void leavesUrlsWithoutQueryUntouched() {
        assertEquals("http://localhost/api/models", UrlQueryRedactor.redact("http://localhost/api/models"));
        assertNull(UrlQueryRedactor.redact(null));
        assertEquals("", UrlQueryRedactor.redact(""));
    }

    @Test
    @DisplayName("URL 片段（#fragment）原样保留，且不会被当成 query 值")
    void keepsFragment() {
        String redacted = UrlQueryRedactor.redact("http://localhost/api?token=secret#section-2");

        assertEquals("http://localhost/api?token=[REDACTED]#section-2", redacted);
    }

    @Test
    @DisplayName("无值参数与空值参数不抛异常")
    void handlesMalformedQuery() {
        assertEquals("http://localhost/api?flag&token=[REDACTED]",
                UrlQueryRedactor.redact("http://localhost/api?flag&token="));
        assertEquals("http://localhost/api?flag", UrlQueryRedactor.redact("http://localhost/api?flag"));
    }

    @Test
    @DisplayName("password / secret / credential 同样按既有口径脱敏")
    void redactsOtherSecretParameters() {
        String redacted = UrlQueryRedactor.redact("/api?password=p1&client_secret=s1&credential=c1");

        assertFalse(redacted.contains("p1"), redacted);
        assertFalse(redacted.contains("s1"), redacted);
        assertFalse(redacted.contains("c1"), redacted);
        assertEquals("/api?password=[REDACTED]&client_secret=[REDACTED]&credential=[REDACTED]", redacted);
    }
}
