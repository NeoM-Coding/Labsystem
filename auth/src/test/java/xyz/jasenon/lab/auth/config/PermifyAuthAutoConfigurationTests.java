package xyz.jasenon.lab.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import xyz.jasenon.lab.auth.aspect.PermifyAuthorizationAspect;
import xyz.jasenon.lab.auth.client.AuthClient;

import static org.assertj.core.api.Assertions.assertThat;

class PermifyAuthAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PermifyAuthAutoConfiguration.class));

    @Test
    void disabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AuthClient.class);
            assertThat(context).doesNotHaveBean(PermifyAuthorizationAspect.class);
        });
    }

    @Test
    void enabledPropertyCreatesClientAndAspect() {
        contextRunner
                .withPropertyValues(
                        "lab.auth.permify.enabled=true",
                        "lab.auth.permify.base-url=http://127.0.0.1:3476",
                        "lab.auth.permify.tenant-id=test"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthClient.class);
                    assertThat(context).hasSingleBean(PermifyAuthorizationAspect.class);
                });
    }
}
