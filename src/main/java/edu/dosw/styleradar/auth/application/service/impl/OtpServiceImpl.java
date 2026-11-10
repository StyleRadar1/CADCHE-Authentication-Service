package edu.dosw.styleradar.auth.application.service.impl;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class OtpServiceImpl implements OtpService {

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    private final Map<String, String> localMemoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> localExpiryStore = new ConcurrentHashMap<>();

    @Value("${styleradar.security.otp.ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${styleradar.security.otp.max-attempts:3}")
    private int maxAttempts;

    public OtpServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generarOtp(String email, Long usuarioId, String rol) {
        String codigo = String.format("%06d", random.nextInt(1000000));

        guardarValor("otp:" + email, codigo, ttlSeconds);
        guardarValor("otp:intentos:" + email, String.valueOf(maxAttempts), ttlSeconds);
        guardarValor("otp:usuario:" + email, String.valueOf(usuarioId), ttlSeconds);
        guardarValor("otp:rol:" + email, rol, ttlSeconds);

        log.info("Código OTP generado para el correo {}", email);
        return codigo;
    }

    @Override
    public void verificarOtp(String email, String codigo) {
        String claveOtp = "otp:" + email;
        String claveIntentos = "otp:intentos:" + email;

        String codigoGuardado = obtenerValor(claveOtp);

        if (codigoGuardado == null) {
            log.warn("Intento de verificación para OTP expirado o inexistente: {}", email);
            throw new OtpExpiradoException("El código OTP expiró o no existe. Debe solicitar uno nuevo.");
        }

        if (codigoGuardado.equals(codigo)) {
            log.info("Código OTP verificado exitosamente para {}", email);
            return;
        }

        Long restantes = decrementarIntentos(claveIntentos);
        log.warn("Código OTP incorrecto para {}. Intentos restantes: {}", email, restantes);

        if (restantes == null || restantes <= 0) {
            eliminarOtp(email);
            log.error("Se agotaron los 3 intentos de verificación OTP para {}", email);
            throw new OtpIntentosAgotadosException(
                    "Se agotaron los intentos disponibles para verificar el OTP. Debe solicitar un nuevo código.");
        }

        throw new OtpInvalidoException("El código OTP es incorrecto. Intentos restantes: " + restantes);
    }

    @Override
    public String reenviarOtp(String email) {
        String usuarioId = obtenerValor("otp:usuario:" + email);
        String rol = obtenerValor("otp:rol:" + email);

        if (usuarioId == null || rol == null) {
            log.warn("Solicitud de reenvío OTP sin sesión activa previa para {}", email);
            throw new OtpExpiradoException(
                    "No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.");
        }

        String nuevoCodigo = String.format("%06d", random.nextInt(1000000));
        guardarValor("otp:" + email, nuevoCodigo, ttlSeconds);
        guardarValor("otp:intentos:" + email, String.valueOf(maxAttempts), ttlSeconds);
        guardarValor("otp:usuario:" + email, usuarioId, ttlSeconds);
        guardarValor("otp:rol:" + email, rol, ttlSeconds);

        log.info("Nuevo código OTP regenerado y reenviado para {}", email);
        return nuevoCodigo;
    }

    @Override
    public Long obtenerUsuarioId(String email) {
        String usuarioId = obtenerValor("otp:usuario:" + email);
        return usuarioId == null ? null : Long.valueOf(usuarioId);
    }

    @Override
    public String obtenerRol(String email) {
        return obtenerValor("otp:rol:" + email);
    }

    @Override
    public int obtenerIntentosRestantes(String email) {
        String valor = obtenerValor("otp:intentos:" + email);
        return valor == null ? 0 : Integer.parseInt(valor);
    }

    @Override
    public void eliminarOtp(String email) {
        eliminarClave("otp:" + email);
        eliminarClave("otp:intentos:" + email);
        eliminarClave("otp:usuario:" + email);
        eliminarClave("otp:rol:" + email);
    }

    @Override
    public void invalidarToken(String token, long ttlSegundos) {
        if (token != null && ttlSegundos > 0) {
            guardarValor("blacklist:" + token, "revoked", ttlSegundos);
            log.info("Token JWT revocado en lista negra con TTL de {}s", ttlSegundos);
        }
    }

    @Override
    public boolean esTokenInvalido(String token) {
        if (token == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
        } catch (DataAccessException ex) {
            String val = localMemoryStore.get("blacklist:" + token);
            Long exp = localExpiryStore.get("blacklist:" + token);
            return val != null && exp != null && System.currentTimeMillis() < exp;
        }
    }

    private void guardarValor(String clave, String valor, long ttl) {
        try {
            redisTemplate.opsForValue().set(clave, valor, ttl, TimeUnit.SECONDS);
        } catch (DataAccessException ex) {
            log.debug("Redis no disponible para guardar clave '{}'. Usando memoria local dev: {}", clave, ex.getMessage());
            localMemoryStore.put(clave, valor);
            localExpiryStore.put(clave, System.currentTimeMillis() + (ttl * 1000));
        }
    }

    private String obtenerValor(String clave) {
        try {
            return redisTemplate.opsForValue().get(clave);
        } catch (DataAccessException ex) {
            Long exp = localExpiryStore.get(clave);
            if (exp != null && System.currentTimeMillis() > exp) {
                localMemoryStore.remove(clave);
                localExpiryStore.remove(clave);
                return null;
            }
            return localMemoryStore.get(clave);
        }
    }

    private Long decrementarIntentos(String clave) {
        try {
            return redisTemplate.opsForValue().decrement(clave);
        } catch (DataAccessException ex) {
            String val = localMemoryStore.get(clave);
            long actual = val != null ? Long.parseLong(val) : maxAttempts;
            long decrementado = actual - 1;
            localMemoryStore.put(clave, String.valueOf(decrementado));
            return decrementado;
        }
    }

    private void eliminarClave(String clave) {
        try {
            redisTemplate.delete(clave);
        } catch (DataAccessException ex) {
            localMemoryStore.remove(clave);
            localExpiryStore.remove(clave);
        }
    }
}
