package edu.dosw.styleradar.auth.application.service.impl;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
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

        guardarEnRedis(email, codigo, String.valueOf(usuarioId), rol, maxAttempts);
        log.info("Código OTP generado y almacenado en Redis para el correo {}", email);

        return codigo;
    }

    @Override
    public void verificarOtp(String email, String codigo) {
        String claveOtp = "otp:" + email;
        String claveIntentos = "otp:intentos:" + email;

        String codigoGuardado = redisTemplate.opsForValue().get(claveOtp);

        if (codigoGuardado == null) {
            log.warn("Intento de verificación para OTP expirado o inexistente: {}", email);
            throw new OtpExpiradoException("El código OTP expiró o no existe. Debe solicitar uno nuevo.");
        }

        if (codigoGuardado.equals(codigo)) {
            log.info("Código OTP verificado exitosamente para {}", email);
            return;
        }

        Long restantes = redisTemplate.opsForValue().decrement(claveIntentos);
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
        String usuarioId = redisTemplate.opsForValue().get("otp:usuario:" + email);
        String rol = redisTemplate.opsForValue().get("otp:rol:" + email);

        if (usuarioId == null || rol == null) {
            log.warn("Solicitud de reenvío OTP sin sesión activa previa para {}", email);
            throw new OtpExpiradoException(
                    "No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.");
        }

        String nuevoCodigo = String.format("%06d", random.nextInt(1000000));
        guardarEnRedis(email, nuevoCodigo, usuarioId, rol, maxAttempts);
        log.info("Nuevo código OTP regenerado y reenviado para {}", email);

        return nuevoCodigo;
    }

    @Override
    public Long obtenerUsuarioId(String email) {
        String usuarioId = redisTemplate.opsForValue().get("otp:usuario:" + email);
        return usuarioId == null ? null : Long.valueOf(usuarioId);
    }

    @Override
    public String obtenerRol(String email) {
        return redisTemplate.opsForValue().get("otp:rol:" + email);
    }

    @Override
    public int obtenerIntentosRestantes(String email) {
        String valor = redisTemplate.opsForValue().get("otp:intentos:" + email);
        return valor == null ? 0 : Integer.parseInt(valor);
    }

    @Override
    public void eliminarOtp(String email) {
        redisTemplate.delete("otp:" + email);
        redisTemplate.delete("otp:intentos:" + email);
        redisTemplate.delete("otp:usuario:" + email);
        redisTemplate.delete("otp:rol:" + email);
    }

    @Override
    public void invalidarToken(String token, long ttlSegundos) {
        if (token != null && ttlSegundos > 0) {
            redisTemplate.opsForValue().set("blacklist:" + token, "revoked", ttlSegundos, TimeUnit.SECONDS);
            log.info("Token JWT revocado y almacenado en lista negra de Redis con TTL de {}s", ttlSegundos);
        }
    }

    @Override
    public boolean esTokenInvalido(String token) {
        if (token == null) {
            return true;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
    }

    private void guardarEnRedis(String email, String codigo, String usuarioId, String rol, int intentos) {
        redisTemplate.opsForValue().set("otp:" + email, codigo, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("otp:intentos:" + email, String.valueOf(intentos), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("otp:usuario:" + email, usuarioId, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("otp:rol:" + email, rol, ttlSeconds, TimeUnit.SECONDS);
    }
}
