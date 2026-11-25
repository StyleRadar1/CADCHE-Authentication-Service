package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import edu.dosw.styleradar.auth.domain.model.EntidadAsociada;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.port.out.EntidadAsociadaPort;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador fail-open hacia ms-tiendas para enriquecer el JWT con {@code storeId} (STORE_ADMIN)
 * o {@code foundationId} (FOUNDATION_REPRESENTATIVE) (BUG-005).
 * Nunca falla ni bloquea el inicio de sesión si ms-tiendas no está configurado, responde 401/404/5xx
 * o supera el tiempo de espera (2s conexión / 3s lectura).
 */
@Component
@Slf4j
public class TiendasEntidadAdapter implements EntidadAsociadaPort {

    static final String INTERNAL_STORES_PATH = "/api/v1/internal/stores";
    static final String INTERNAL_FOUNDATIONS_PATH = "/api/v1/internal/foundations";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public TiendasEntidadAdapter(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String internalUsername,
            String internalPassword) {
        this(restClientBuilder, baseUrl, internalUsername, internalPassword, null, false);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public TiendasEntidadAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${styleradar.tiendas.base-url:}") String baseUrl,
            @Value("${styleradar.tiendas.internal-username:ms-auth}") String internalUsername,
            @Value("${styleradar.tiendas.internal-password:}") String internalPassword,
            Environment environment) {
        this(restClientBuilder, baseUrl, internalUsername, internalPassword, environment, true);
    }

    private TiendasEntidadAdapter(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String internalUsername,
            String internalPassword,
            Environment environment,
            boolean aplicarTimeoutsJdk) {
        this.restClient = construirClienteSeguro(
                restClientBuilder, baseUrl, internalUsername, internalPassword, environment, aplicarTimeoutsJdk);
    }

    private static RestClient construirClienteSeguro(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String internalUsername,
            String internalPassword,
            Environment environment,
            boolean aplicarTimeoutsJdk) {

        if (restClientBuilder == null
                || baseUrl == null || baseUrl.isBlank()
                || internalPassword == null || internalPassword.isBlank()) {
            log.info("Enriquecimiento de claims desde ms-tiendas deshabilitado (base-url o internal-password no configurados)");
            return null;
        }

        String validatedUrl;
        try {
            validatedUrl = SecuritySanitizer.validateExternalServiceUrl(
                    baseUrl, environment, "TIENDAS_API_BASE_URL");
        } catch (IllegalArgumentException ex) {
            log.warn("URL inválida para ms-tiendas ({}); enriquecimiento fail-open deshabilitado: {}",
                    SecuritySanitizer.sanitizeForLog(baseUrl), SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            return null;
        }

        String username = (internalUsername == null || internalUsername.isBlank())
                ? "ms-auth"
                : internalUsername.trim();

        RestClient.Builder builder = restClientBuilder
                .baseUrl(validatedUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(username, internalPassword));

        if (aplicarTimeoutsJdk) {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(READ_TIMEOUT);
            builder = builder.requestFactory(requestFactory);
        }

        return builder.build();
    }

    @Override
    public EntidadAsociada resolverEntidadAsociada(Long usuarioId, Set<Rol> roles) {
        if (restClient == null || usuarioId == null || roles == null || roles.isEmpty()) {
            return EntidadAsociada.VACIA;
        }

        String storeId = roles.contains(Rol.STORE_ADMIN) ? consultarStoreId(usuarioId) : null;
        Long foundationId = roles.contains(Rol.FOUNDATION_REPRESENTATIVE) ? consultarFoundationId(usuarioId) : null;
        return new EntidadAsociada(storeId, foundationId);
    }

    private String consultarStoreId(Long usuarioId) {
        try {
            TiendaInternaDTO dto = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(INTERNAL_STORES_PATH)
                            .queryParam("ownerUserId", usuarioId)
                            .build())
                    .retrieve()
                    .body(TiendaInternaDTO.class);
            if (dto == null) {
                return null;
            }
            String nit = (dto.nit() != null && !dto.nit().isBlank()) ? dto.nit() : dto.storeNit();
            return (nit != null && !nit.isBlank()) ? nit.trim() : null;
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("Fallo fail-open consultando tienda para usuarioId={}: {}",
                    usuarioId, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            return null;
        }
    }

    private Long consultarFoundationId(Long usuarioId) {
        try {
            FundacionInternaDTO dto = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(INTERNAL_FOUNDATIONS_PATH)
                            .queryParam("representativeId", usuarioId)
                            .build())
                    .retrieve()
                    .body(FundacionInternaDTO.class);
            return dto != null ? dto.id() : null;
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("Fallo fail-open consultando fundación para usuarioId={}: {}",
                    usuarioId, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TiendaInternaDTO(String nit, String storeNit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FundacionInternaDTO(Long id) {
    }
}
