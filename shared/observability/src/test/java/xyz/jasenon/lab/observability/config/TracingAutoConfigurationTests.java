package xyz.jasenon.lab.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import xyz.jasenon.lab.observability.aspect.TracedAspect;
import xyz.jasenon.lab.observability.log.SafeArgumentRenderer;

import static org.assertj.core.api.Assertions.assertThat;

class TracingAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class));

    @Test
    void coreTracingLoadsWithoutServletApi() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("jakarta.servlet"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SafeArgumentRenderer.class);
                    assertThat(context).hasSingleBean(TracedAspect.class);
                });
    }
}
