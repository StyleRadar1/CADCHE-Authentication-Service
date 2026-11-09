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

@Service
public class OtpServiceImpl implements OtpService {

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

        redisTemplate.opsForValue().set("otp:" + email, codigo, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("otp:intentos:" + email,
                String.valueOf(maxAttempts), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("otp:usuario:" + email,
                String.valueOf(usuarioId), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("otp:rol:" + email,
                rol, ttlSeconds, TimeUnit.SECONDS);

        return codigo;
    }

    @Override
    public void verificarOtp(String email, String codigo) {
        String claveOtp = "otp:" + email;
        String claveIntentos = "otp:intentos:" + email;

        String codigoGuardado = redisTemplate.opsForValue().get(claveOtp);

        if (codigoGuardado == null) {
            throw new OtpExpiradoException("El código OTP expiró o no existe");
        }

        if (codigoGuardado.equals(codigo)) {
            return;
        }

        Long restantes = redisTemplate.opsForValue().decrement(claveIntentos);

        if (restantes == null || restantes <= 0) {
            eliminarOtp(email);
            throw new OtpIntentosAgotadosException(
                    "Se agotaron los intentos disponibles para verificar el OTP");
        }

        throw new OtpInvalidoException("El código OTP es incorrecto");
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
}