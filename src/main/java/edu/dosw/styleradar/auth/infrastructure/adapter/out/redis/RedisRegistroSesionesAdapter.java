package edu.dosw.styleradar.auth.infrastructure.adapter.out.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.domain.model.SesionActiva;
import edu.dosw.styleradar.auth.domain.port.out.RegistroSesionesPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Sesiones abiertas en Redis:
 * <ul>
 *   <li>{@code sesion:<jti>}: hash con sesionId, usuarioId y vencimiento del token.</li>
 *   <li>{@code sesiones:vencimiento}: sorted set de jti por vencimiento; quitar un jti de aquí es la operación
 *       atómica que decide qué instancia publica el -1.</li>
 *   <li>{@code sesiones:usuario:<id>}: set con los jti abiertos del usuario.</li>
 * </ul>
 */
@Component
public class RedisRegistroSesionesAdapter implements RegistroSesionesPort {

    static final String VENCIMIENTOS = "sesiones:vencimiento";
    private static final Duration MARGEN = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Bean de Spring inyectado por constructor.")
    public RedisRegistroSesionesAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String clave(String tokenId) {
        return "sesion:" + tokenId;
    }

    static String claveUsuario(Long usuarioId) {
        return "sesiones:usuario:" + usuarioId;
    }

    @Override
    public void registrar(SesionActiva sesion) {
        Date limpieza = Date.from(sesion.expira().plus(MARGEN));
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("sesionId", sesion.sesionId());
        datos.put("usuarioId", String.valueOf(sesion.usuarioId()));
        datos.put("expira", String.valueOf(sesion.expira().toEpochMilli()));
        redis.opsForHash().putAll(clave(sesion.tokenId()), datos);
        redis.expireAt(clave(sesion.tokenId()), limpieza);
        redis.opsForZSet().add(VENCIMIENTOS, sesion.tokenId(), sesion.expira().toEpochMilli());
        if (sesion.usuarioId() != null) {
            redis.opsForSet().add(claveUsuario(sesion.usuarioId()), sesion.tokenId());
            redis.expireAt(claveUsuario(sesion.usuarioId()), limpieza);
        }
    }

    @Override
    public Optional<SesionActiva> finalizar(String tokenId) {
        if (tokenId == null) {
            return Optional.empty();
        }
        Long quitadas = redis.opsForZSet().remove(VENCIMIENTOS, tokenId);
        if (quitadas == null || quitadas == 0) {
            return Optional.empty();
        }
        Map<Object, Object> datos = redis.opsForHash().entries(clave(tokenId));
        redis.delete(clave(tokenId));
        Long usuarioId = numero(datos.get("usuarioId"));
        if (usuarioId != null) {
            redis.opsForSet().remove(claveUsuario(usuarioId), tokenId);
        }
        Object sesionId = datos.get("sesionId");
        Long expira = numero(datos.get("expira"));
        return Optional.of(new SesionActiva(sesionId != null ? sesionId.toString() : tokenId, usuarioId, tokenId,
                expira != null ? Instant.ofEpochMilli(expira) : null));
    }

    @Override
    public Optional<SesionActiva> reemplazar(String tokenIdAnterior, String tokenIdNuevo, Instant expiraNueva) {
        return finalizar(tokenIdAnterior).map(s -> {
            SesionActiva renovada = new SesionActiva(s.sesionId(), s.usuarioId(), tokenIdNuevo, expiraNueva);
            registrar(renovada);
            return renovada;
        });
    }

    @Override
    public List<SesionActiva> finalizarVencidas(Instant ahora, int limite) {
        Set<String> vencidas = redis.opsForZSet().rangeByScore(VENCIMIENTOS, 0, ahora.toEpochMilli(), 0, limite);
        return finalizarTodas(vencidas);
    }

    @Override
    public List<SesionActiva> finalizarDeUsuario(Long usuarioId) {
        return usuarioId == null ? List.of() : finalizarTodas(redis.opsForSet().members(claveUsuario(usuarioId)));
    }

    private List<SesionActiva> finalizarTodas(Set<String> tokenIds) {
        List<SesionActiva> cerradas = new ArrayList<>();
        if (tokenIds != null) {
            tokenIds.forEach(id -> finalizar(id).ifPresent(cerradas::add));
        }
        return cerradas;
    }

    private static Long numero(Object valor) {
        try {
            return valor != null && !"null".equals(valor.toString()) ? Long.valueOf(valor.toString()) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
