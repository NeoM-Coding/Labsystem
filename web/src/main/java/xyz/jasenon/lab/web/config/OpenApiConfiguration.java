package xyz.jasenon.lab.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI labSystemOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lab System Cloud API")
                        .description("实验室管理系统 Web 网关接口")
                        .version("0.0.1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("saToken", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("satoken")));
    }
}
