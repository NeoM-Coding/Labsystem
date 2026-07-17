package xyz.jasenon.lab.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import xyz.jasenon.lab.auth.aspect.PermifyAuthorizationAspect;
import xyz.jasenon.lab.auth.client.AuthClient;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;

@AutoConfiguration
@EnableConfigurationProperties(PermifyAuthProperties.class)
@ConditionalOnProperty(prefix = "lab.auth.permify", name = "enabled", havingValue = "true")
public class PermifyAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthorizationOperations.class)
    public AuthClient authClient(PermifyAuthProperties properties) {
        return new AuthClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public PermifyAuthorizationAspect permifyAuthorizationAspect(AuthorizationOperations authorizationOperations) {
        return new PermifyAuthorizationAspect(authorizationOperations);
    }
}
