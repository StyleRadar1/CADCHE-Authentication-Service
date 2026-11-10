package edu.dosw.styleradar.auth.infrastructure.config;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

    @Bean
    OpenAPI authServiceOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT emitido por ms-auth (HS256)")))
                .servers(List.of(
                        new Server().url("http://localhost:8100").description("Servidor local ms-auth (:8100)"),
                        new Server().url("http://localhost:8080").description("API Gateway StyleRadar (:8080)")))
                .info(new Info()
                        .title("StyleRadar - Authentication Service API (ms-auth)")
                        .description("Especificación OpenAPI 3.0 del microservicio de autenticación de StyleRadar. "
                                + "Gobierna el inicio de sesión con JWT (SR-RF-02), la verificación de segundo factor mediante código OTP de 6 dígitos en Redis (SR-RF-92), "
                                + "el registro atómico de credenciales y la revocación de sesiones en lista negra (SR-RNF-01).")
                        .version("v1")
                        .contact(new Contact()
                                .name("Equipo CADche - StyleRadar DOSW")
                                .email("cadche@styleradar.com")));
    }

    @Bean
    OpenApiCustomizer authenticationDocumentation() {
        Set<String> publicPosts = Set.of(
                "/api/v1/auth/login",
                "/api/v1/auth/registro",
                "/api/v1/auth/otp/verificar",
                "/api/v1/auth/otp/reenviar",
                "/api/v1/auth/refresh");
        return api -> {
            if (api.getPaths() == null) {
                return;
            }
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean publicRoute = method == PathItem.HttpMethod.POST && publicPosts.contains(path);
                operation.setSecurity(publicRoute
                        ? List.of()
                        : List.of(new SecurityRequirement().addList("bearerAuth")));
            }));
        };
    }
}

