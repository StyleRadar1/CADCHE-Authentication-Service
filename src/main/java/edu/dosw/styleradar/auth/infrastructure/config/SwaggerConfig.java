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
                        .description("JWT issued by ms-auth (HS256)")))
                .servers(List.of(
                        new Server().url("/").description("Path relative to the current host (recommended)"),
                        new Server().url("http://localhost:8100").description("Local ms-auth server (:8100)"),
                        new Server().url("http://localhost:8080").description("StyleRadar API Gateway (:8080)")))
                .info(new Info()
                        .title("StyleRadar - Authentication Service API (ms-auth)")
                        .description("OpenAPI 3.0 specification of the StyleRadar authentication microservice. "
                                + "Covers JWT login, one-time email verification with a 6-digit OTP code stored in Redis, "
                                + "Google OAuth2 / OpenID Connect sign-in, token refresh and logout with blacklist revocation. "
                                + "Routes, fields, values and error codes are in English; only the user messages inside the JSON are in Spanish.")
                        .version("v1")
                        .contact(new Contact()
                                .name("CADche team - StyleRadar DOSW")
                                .email("cadche@styleradar.com")));
    }

    @Bean
    OpenApiCustomizer authenticationDocumentation() {
        Set<String> publicPosts = Set.of(
                "/api/v1/auth/login",
                "/api/v1/auth/otp/verify",
                "/api/v1/auth/otp/resend",
                "/api/v1/auth/refresh",
                "/api/v1/auth/oauth2/google",
                "/api/v1/auth/oauth2/google/code");
        Set<String> publicGets = Set.of(
                "/api/v1/auth/oauth2/google/url",
                "/api/v1/auth/oauth2/google/callback");

        return api -> {
            if (api.getPaths() == null) {
                return;
            }

            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean publicRoute = (method == PathItem.HttpMethod.POST && publicPosts.contains(path))
                        || (method == PathItem.HttpMethod.GET && publicGets.contains(path));
                operation.setSecurity(publicRoute
                        ? List.of()
                        : List.of(new SecurityRequirement().addList("bearerAuth")));
            }));
        };
    }
}
