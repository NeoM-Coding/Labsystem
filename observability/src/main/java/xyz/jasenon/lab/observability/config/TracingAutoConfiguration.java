package xyz.jasenon.lab.observability.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import xyz.jasenon.lab.observability.aspect.TracedAspect;
import xyz.jasenon.lab.observability.http.TraceHttpFilter;
import xyz.jasenon.lab.observability.log.SafeArgumentRenderer;

@AutoConfiguration
@EnableConfigurationProperties(TracingProperties.class)
@ConditionalOnProperty(prefix = "lab.observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SafeArgumentRenderer safeArgumentRenderer(TracingProperties properties) {
        return new SafeArgumentRenderer(properties.getMaxArgumentLength(), properties.getMaxCollectionSize(), properties.getMaxDepth());
    }

    @Bean
    @ConditionalOnMissingBean
    TracedAspect tracedAspect(SafeArgumentRenderer renderer) {
        return new TracedAspect(renderer);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    TraceHttpFilter traceHttpFilter() {
        return new TraceHttpFilter();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    FilterRegistrationBean<TraceHttpFilter> traceHttpFilterRegistration(TraceHttpFilter filter) {
        FilterRegistrationBean<TraceHttpFilter> registration = new FilterRegistrationBean<>(filter);
        // Trace must wrap authentication so rejected requests also receive correlation IDs.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
