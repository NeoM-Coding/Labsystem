package xyz.jasenon.lab.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import xyz.jasenon.lab.auth.aspect.ActionAuthorizationAspect;
import xyz.jasenon.lab.auth.client.AuthClient;
import xyz.jasenon.lab.auth.service.Auth;
import xyz.jasenon.lab.auth.service.AuthService;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorizationService;

import static org.assertj.core.api.Assertions.assertThat;

class PermifyAuthAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PermifyAuthAutoConfiguration.class));

    @Test
    void createsRequiredAuthorizationBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthClient.class);
            assertThat(context).hasSingleBean(ActionAuthorizationAspect.class);
            assertThat(context).hasSingleBean(Auth.class);
            assertThat(context).hasSingleBean(AuthService.class);
            assertThat(context).hasSingleBean(LaboratoryAuthorization.class);
            assertThat(context).hasSingleBean(LaboratoryAuthorizationService.class);
        });
    }

    @Test
    void appliesPermifyConnectionPropertiesWithoutEnableSwitch() {
        contextRunner
                .withPropertyValues(
                        "lab.auth.permify.base-url=http://127.0.0.1:3476",
                        "lab.auth.permify.tenant-id=test"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthClient.class);
                    assertThat(context).hasSingleBean(ActionAuthorizationAspect.class);
                    assertThat(context).hasSingleBean(AuthService.class);
                    assertThat(context).hasSingleBean(LaboratoryAuthorizationService.class);
                });
    }
}
