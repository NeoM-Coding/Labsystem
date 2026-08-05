package xyz.jasenon.lab.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import xyz.jasenon.lab.auth.aspect.ActionAuthorizationAspect;
import xyz.jasenon.lab.auth.client.AuthClient;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.handler.ActionCommandHandler;
import xyz.jasenon.lab.auth.handler.ActionCommandHandlerRegistry;
import xyz.jasenon.lab.auth.service.Auth;
import xyz.jasenon.lab.auth.service.AuthService;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorizationService;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(PermifyAuthProperties.class)
public class PermifyAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthorizationOperations.class)
    public AuthClient authClient(PermifyAuthProperties properties) {
        return new AuthClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean(Auth.class)
    public AuthService authService(AuthorizationOperations authorizationOperations) {
        return new AuthService(authorizationOperations);
    }

    @Bean
    @ConditionalOnMissingBean(LaboratoryAuthorization.class)
    public LaboratoryAuthorizationService laboratoryAuthorizationService(
            AuthorizationOperations authorizationOperations) {
        return new LaboratoryAuthorizationService(authorizationOperations);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionCommandHandlerRegistry actionCommandHandlerRegistry(List<ActionCommandHandler<?>> handlers) {
        return new ActionCommandHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionAuthorizationAspect actionAuthorizationAspect(ActionCommandHandlerRegistry registry, Auth auth) {
        return new ActionAuthorizationAspect(registry, auth);
    }
}
