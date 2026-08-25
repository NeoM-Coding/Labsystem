package xyz.jasenon.lab.observability.config;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import xyz.jasenon.lab.observability.http.TraceHttpFilter;

/**
 * Servlet 专用的 HTTP 链路追踪配置。
 *
 * <p>该配置与核心 tracing 配置隔离，避免纯 RPC 服务在没有 Servlet API 时
 * 因 Spring 反射配置类方法签名而启动失败。</p>
 */
@AutoConfiguration(after = TracingAutoConfiguration.class)
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "lab.observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TraceHttpFilter traceHttpFilter() {
        return new TraceHttpFilter();
    }

    @Bean
    FilterRegistrationBean<TraceHttpFilter> traceHttpFilterRegistration(TraceHttpFilter filter) {
        FilterRegistrationBean<TraceHttpFilter> registration = new FilterRegistrationBean<>(filter);
        // Trace must wrap authentication so rejected requests also receive correlation IDs.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
