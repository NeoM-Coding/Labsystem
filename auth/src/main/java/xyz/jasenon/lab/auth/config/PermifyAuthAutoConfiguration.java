package xyz.jasenon.lab.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import xyz.jasenon.lab.auth.aspect.ActionAuthorizationAspect;
import xyz.jasenon.lab.auth.client.AuthClient;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.handler.ActionCommandHandlerRegistry;
import xyz.jasenon.lab.auth.service.Auth;
import xyz.jasenon.lab.auth.service.AuthService;
import xyz.jasenon.lab.auth.service.DisabledAuthService;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(PermifyAuthProperties.class)
public class PermifyAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthorizationOperations.class)
    @ConditionalOnProperty(prefix = "lab.auth.permify", name = "enabled", havingValue = "true")
    public AuthClient authClient(PermifyAuthProperties properties) {
        return new AuthClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean(Auth.class)
    @ConditionalOnProperty(prefix = "lab.auth.permify", name = "enabled", havingValue = "true")
    public AuthService authService(AuthorizationOperations authorizationOperations) {
        return new AuthService(authorizationOperations);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lab.auth.permify", name = "enabled", havingValue = "true")
    public ActionCommandHandlerRegistry actionCommandHandlerRegistry(List<ActionCommandHandler<?>> handlers) {
        return new ActionCommandHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lab.auth.permify", name = "enabled", havingValue = "true")
    public ActionAuthorizationAspect actionAuthorizationAspect(ActionCommandHandlerRegistry registry, Auth auth) {
        return new ActionAuthorizationAspect(registry, auth);
    }

    @Bean
    @ConditionalOnMissingBean(Auth.class)
    @ConditionalOnProperty(
            prefix = "lab.auth.permify",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public DisabledAuthService disabledAuthService() {
        return new DisabledAuthService();
    }
}
