package xyz.jasenon.lab.observability.log;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafeArgumentRendererTests {

    private final SafeArgumentRenderer renderer = new SafeArgumentRenderer(256, 10, 3);

    @Test
    void redactsSensitiveMapAndObjectFields() {
        String rendered = renderer.render(Map.of(
                "username", "jasenon",
                "token", "should-not-leak",
                "payload", new Login("user", "secret-password")
        ));

        assertThat(rendered)
                .contains("username=jasenon", "token=***", "password=***")
                .doesNotContain("should-not-leak", "secret-password");
    }

    @Test
    void limitsOutputLength() {
        assertThat(renderer.render(Map.of("value", "x".repeat(500))))
                .hasSizeLessThanOrEqualTo(271)
                .endsWith("...[truncated]");
    }

    private record Login(String username, String password) {
    }
}
