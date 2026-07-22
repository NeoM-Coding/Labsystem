package xyz.jasenon.lab.web.config;

import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class SaTokenFilterConfiguration {

    @Bean
    FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> saTokenContextFilterRegistration(
            SaTokenContextFilterForJakartaServlet filter) {
        FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
