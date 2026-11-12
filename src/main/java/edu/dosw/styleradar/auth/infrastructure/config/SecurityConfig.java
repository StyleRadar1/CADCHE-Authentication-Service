package edu.dosw.styleradar.auth.infrastructure.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.infrastructure.security.JwtAuthenticationFilter;
import edu.dosw.styleradar.auth.infrastructure.security.RateLimitingFilter;
import edu.dosw.styleradar.auth.infrastructure.security.SecurityErrorResponseWriter;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

        private final ObjectMapper objectMapper;
        private final String allowedOriginsCsv;

        @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "ObjectMapper administrado por Spring e inyectado por constructor.")
        public SecurityConfig(
                        ObjectMapper objectMapper,
                        @Value("${styleradar.cors.allowed-origins:https://styleradar.com,https://*.styleradar.com,https://*.azurewebsites.net}") String allowedOriginsCsv) {
                this.objectMapper = objectMapper;
                this.allowedOriginsCsv = allowedOriginsCsv;
        }

        @Bean
        SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        RateLimitingFilter rateLimitingFilter) throws Exception {

                return http
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())
                                .logout(logout -> logout.disable())
                                .requestCache(cache -> cache.disable())
                                .headers(headers -> headers
                                                .contentTypeOptions(Customizer.withDefaults())
                                                .frameOptions(frame -> frame.deny())
                                                .referrerPolicy(referrer -> referrer
                                                                .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .preload(true)
                                                                .maxAgeInSeconds(31536000)))
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint(authenticationEntryPoint())
                                                .accessDeniedHandler(accessDeniedHandler()))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.GET,
                                                                "/swagger-ui.html",
                                                                "/swagger-ui/**",
                                                                "/v3/api-docs",
                                                                "/v3/api-docs/**",
                                                                "/actuator/health",
                                                                "/actuator/health/**",
                                                                "/actuator/info",
                                                                "/api/v1/auth/oauth2/google/url",
                                                                "/api/v1/auth/oauth2/google/callback")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/v1/auth/login",
                                                                "/api/v1/auth/registro",
                                                                "/api/v1/auth/otp/verificar",
                                                                "/api/v1/auth/otp/reenviar",
                                                                "/api/v1/auth/refresh",
                                                                "/api/v1/auth/oauth2/google",
                                                                "/api/v1/auth/oauth2/google/code")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .addFilterBefore(
                                                rateLimitingFilter,
                                                UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(
                                                jwtAuthenticationFilter,
                                                UsernamePasswordAuthenticationFilter.class)
                                .build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
                List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toList();

                if (origins.isEmpty() || origins.contains("*")) {
                        throw new IllegalArgumentException(
                                        "Configuración CORS insegura (OWASP A02:2025): no se permite lista vacía ni comodín '*' con credenciales.");
                }

                var cors = new CorsConfiguration();
                cors.setAllowedOriginPatterns(origins);
                cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                cors.setAllowedHeaders(
                                List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
                cors.setExposedHeaders(List.of("Authorization", "WWW-Authenticate"));
                cors.setAllowCredentials(true);
                cors.setMaxAge(3600L);

                var source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", cors);
                return source;
        }

        @Bean
        AuthenticationEntryPoint authenticationEntryPoint() {
                return (request, response, authException) -> {
                        String safePath = SecuritySanitizer.sanitizeForLog(request.getRequestURI());
                        log.warn("Acceso no autenticado bloqueado (401) en ruta {}", safePath);
                        SecurityErrorResponseWriter.write(
                                        response, objectMapper, HttpStatus.UNAUTHORIZED, safePath,
                                        "No autorizado: se requiere un token Bearer válido");
                };
        }

        @Bean
        AccessDeniedHandler accessDeniedHandler() {
                return (request, response, accessDeniedException) -> {
                        String safePath = SecuritySanitizer.sanitizeForLog(request.getRequestURI());
                        log.warn("Acceso denegado por permisos insuficientes (403) en ruta {}", safePath);
                        SecurityErrorResponseWriter.write(
                                        response, objectMapper, HttpStatus.FORBIDDEN, safePath,
                                        "Acceso denegado: no cuenta con permisos suficientes");
                };
        }
}
