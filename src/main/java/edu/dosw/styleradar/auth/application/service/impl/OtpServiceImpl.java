package edu.dosw.styleradar.auth.application.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class OtpServiceImpl implements OtpService {

    private static final String KEY_OTP = "otp:";
    private static final String KEY_INTENTOS = "otp:intentos:";
    private static final String KEY_REENVIOS = "otp:reenvios:";
    private static final String KEY_USUARIO = "otp:usuario:";
    private static final String KEY_ROL = "otp:rol:";
    private static final String KEY_BLACKLIST = "blacklist:";
    private static final String KEY_USUARIO_REVOCADO = "usuario:revocado:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();
    private final boolean localFallbackEnabled;
    private final Environment environment;

    private final Map<String, String> localMemoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> localExpiryStore = new ConcurrentHashMap<>();

    @Value("${styleradar.security.otp.ttl-seconds:300}")
    private long ttlSeconds = 300L;

    @Value("${styleradar.security.otp.max-attempts:3}")
    private int maxAttempts = 3;

    @Value("${styleradar.security.otp.max-resends:3}")
    private int maxResends = 3;

    public OtpServiceImpl(StringRedisTemplate redisTemplate) {
        this(redisTemplate, false, null);
    }

    public OtpServiceImpl(StringRedisTemplate redisTemplate, boolean localFallbackEnabled) {
        this(redisTemplate, localFallbackEnabled, null);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public OtpServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${styleradar.security.redis-local-fallback-enabled:false}") boolean localFallbackEnabled,
            Environment environment) {
        this.redisTemplate = redisTemplate;
        this.localFallbackEnabled = localFallbackEnabled;
        this.environment = environment;
    }

    @Override
    public String generarOtp(String email, Long usuarioId, String rol) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String codigo = String.format("%06d", random.nextInt(1_000_000));
        String hashOtp = hashearOtp(codigo);

        guardarValor(KEY_OTP + normalizedEmail, hashOtp, ttlSeconds);
        guardarValor(KEY_INTENTOS + normalizedEmail, String.valueOf(maxAttempts), ttlSeconds);
        guardarValor(KEY_USUARIO + normalizedEmail, String.valueOf(usuarioId), ttlSeconds);
        guardarValor(KEY_ROL + normalizedEmail, rol, ttlSeconds);
        eliminarClave(KEY_REENVIOS + normalizedEmail);

        log.info("Código OTP generado para {}", SecuritySanitizer.maskEmail(normalizedEmail));
        return codigo;
    }

    @Override
    public void verificarOtp(String email, String codigo) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String maskedEmail = SecuritySanitizer.maskEmail(normalizedEmail);
        String claveOtp = KEY_OTP + normalizedEmail;
        String claveIntentos = KEY_INTENTOS + normalizedEmail;

        String hashGuardado = obtenerValor(claveOtp);

        if (hashGuardado == null) {
            log.warn("Intento de verificación para OTP expirado o inexistente: {}", maskedEmail);
            throw new OtpExpiradoException("El código OTP expiró o no existe. Debe solicitar uno nuevo.");
        }

        if (verificarHashOtp(hashGuardado, codigo)) {
            log.info("Código OTP verificado exitosamente para {}", maskedEmail);
            return;
        }

        Long restantes = decrementarIntentos(claveIntentos);
        log.warn("Código OTP incorrecto para {}. Intentos restantes: {}", maskedEmail, restantes);

        if (restantes == null || restantes <= 0) {
            eliminarOtp(normalizedEmail);
            log.error("Se agotaron los intentos de verificación OTP para {}", maskedEmail);
            throw new OtpIntentosAgotadosException(
                    "Se agotaron los intentos disponibles para verificar el OTP. Debe solicitar un nuevo código.");
        }

        throw new OtpInvalidoException("El código OTP es incorrecto. Intentos restantes: " + restantes);
    }

    @Override
    public String reenviarOtp(String email) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String maskedEmail = SecuritySanitizer.maskEmail(normalizedEmail);

        String usuarioId = obtenerValor(KEY_USUARIO + normalizedEmail);
        String rol = obtenerValor(KEY_ROL + normalizedEmail);

        if (usuarioId == null || rol == null) {
            log.warn("Solicitud de reenvío OTP sin sesión activa previa para {}", maskedEmail);
            throw new OtpExpiradoException(
                    "No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.");
        }

        long reenvios = incrementarContador(KEY_REENVIOS + normalizedEmail, ttlSeconds);
        if (reenvios > maxResends) {
            eliminarOtp(normalizedEmail);
            log.warn("Límite de reenvíos OTP excedido ({}/{}) para {}", reenvios, maxResends, maskedEmail);
            throw new OtpIntentosAgotadosException(
                    "Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.");
        }

        String nuevoCodigo = String.format("%06d", random.nextInt(1_000_000));
        String nuevoHash = hashearOtp(nuevoCodigo);

        guardarValor(KEY_OTP + normalizedEmail, nuevoHash, ttlSeconds);
        guardarValor(KEY_INTENTOS + normalizedEmail, String.valueOf(maxAttempts), ttlSeconds);
        guardarValor(KEY_USUARIO + normalizedEmail, usuarioId, ttlSeconds);
        guardarValor(KEY_ROL + normalizedEmail, rol, ttlSeconds);

        log.info("Nuevo código OTP regenerado y reenviado para {}", maskedEmail);
        return nuevoCodigo;
    }

    @Override
    public Long obtenerUsuarioId(String email) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String usuarioId = obtenerValor(KEY_USUARIO + normalizedEmail);
        return usuarioId == null ? null : Long.valueOf(usuarioId);
    }

    @Override
    public String obtenerRol(String email) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        return obtenerValor(KEY_ROL + normalizedEmail);
    }

    @Override
    public int obtenerIntentosRestantes(String email) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String valor = obtenerValor(KEY_INTENTOS + normalizedEmail);
        return valor == null ? 0 : Integer.parseInt(valor);
    }

    @Override
    public void eliminarOtp(String email) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        eliminarClave(KEY_OTP + normalizedEmail);
        eliminarClave(KEY_INTENTOS + normalizedEmail);
        eliminarClave(KEY_REENVIOS + normalizedEmail);
        eliminarClave(KEY_USUARIO + normalizedEmail);
        eliminarClave(KEY_ROL + normalizedEmail);
    }

    @Override
    public void invalidarToken(String token, long ttlSegundos) {
        if (token != null && !token.isBlank() && ttlSegundos > 0) {
            guardarValor(KEY_BLACKLIST + token.trim(), "revoked", ttlSegundos);
            log.info("Token JWT revocado en lista negra con TTL de {}s", ttlSegundos);
        }
    }

    @Override
    public boolean esTokenInvalido(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String clave = KEY_BLACKLIST + token.trim();
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(clave));
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                return tieneValorVigenteLocal(clave);
            }
            log.error("Fallo fail-closed consultando blacklist JWT en Redis: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible verificar la validez de la sesión", ex);
        }
    }

    @Override
    public boolean estaUsuarioRevocado(Long usuarioId, long fechaEmisionMillis) {
        if (usuarioId == null) {
            return true;
        }
        String fechaRevocacion = obtenerValor(KEY_USUARIO_REVOCADO + usuarioId);
        if (fechaRevocacion == null) {
            return false;
        }
        try {
            return fechaEmisionMillis <= Long.parseLong(fechaRevocacion);
        } catch (NumberFormatException ex) {
            log.error("Marca temporal de revocación inválida para usuario ID {}", usuarioId);
            return true;
        }
    }

    /**
     * Genera un hash SHA-256 con sal aleatoria (16 bytes) para evitar almacenar
     * códigos OTP en texto claro en Redis (OWASP A04:2025).
     */
    public String hashearOtp(String codigo) {
        if (codigo == null) {
            throw new IllegalArgumentException("El código OTP es obligatorio");
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] digest = sha256(salt, codigo);
        return Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(digest);
    }

    private boolean verificarHashOtp(String valorAlmacenado, String codigoIngresado) {
        if (valorAlmacenado == null || codigoIngresado == null) {
            return false;
        }
        int separator = valorAlmacenado.indexOf('$');
        if (separator <= 0 || separator == valorAlmacenado.length() - 1) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(valorAlmacenado.substring(0, separator));
            byte[] esperado = Base64.getDecoder().decode(valorAlmacenado.substring(separator + 1));
            byte[] calculado = sha256(salt, codigoIngresado);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] sha256(byte[] salt, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", ex);
        }
    }

    private boolean isDevFallbackActive() {
        if (!localFallbackEnabled) {
            return false;
        }
        return environment == null || SecuritySanitizer.isDevProfileOnly(environment);
    }

    private boolean tieneValorVigenteLocal(String clave) {
        Long exp = localExpiryStore.get(clave);
        if (exp != null && System.currentTimeMillis() > exp) {
            localMemoryStore.remove(clave);
            localExpiryStore.remove(clave);
            return false;
        }
        return localMemoryStore.containsKey(clave);
    }

    private void guardarValor(String clave, String valor, long ttl) {
        try {
            redisTemplate.opsForValue().set(clave, valor, ttl, TimeUnit.SECONDS);
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                log.debug("Redis no disponible en perfil dev. Usando memoria local: {}",
                        SecuritySanitizer.sanitizeForLog(ex.getMessage()));
                localMemoryStore.put(clave, valor);
                localExpiryStore.put(clave, System.currentTimeMillis() + (ttl * 1000L));
                return;
            }
            log.error("Fallo fail-closed guardando clave OTP en Redis: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException("Servicio de sesiones OTP no disponible temporalmente", ex);
        }
    }

    private String obtenerValor(String clave) {
        try {
            return redisTemplate.opsForValue().get(clave);
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                Long exp = localExpiryStore.get(clave);
                if (exp != null && System.currentTimeMillis() > exp) {
                    localMemoryStore.remove(clave);
                    localExpiryStore.remove(clave);
                    return null;
                }
                return localMemoryStore.get(clave);
            }
            log.error("Fallo fail-closed leyendo Redis: {}", SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException("Servicio de sesiones OTP no disponible temporalmente", ex);
        }
    }

    private Long decrementarIntentos(String clave) {
        try {
            return redisTemplate.opsForValue().decrement(clave);
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                String val = localMemoryStore.get(clave);
                long actual = val != null ? Long.parseLong(val) : maxAttempts;
                long decrementado = actual - 1;
                localMemoryStore.put(clave, String.valueOf(decrementado));
                return decrementado;
            }
            log.error("Fallo fail-closed decrementando intentos OTP en Redis: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException("Servicio de sesiones OTP no disponible temporalmente", ex);
        }
    }

    private long incrementarContador(String clave, long ttl) {
        try {
            Long valor = redisTemplate.opsForValue().increment(clave);
            long count = valor != null ? valor : 1L;
            if (count == 1L) {
                redisTemplate.expire(clave, ttl, TimeUnit.SECONDS);
            }
            return count;
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                String current = localMemoryStore.get(clave);
                long count = (current != null ? Long.parseLong(current) : 0L) + 1L;
                localMemoryStore.put(clave, String.valueOf(count));
                localExpiryStore.put(clave, System.currentTimeMillis() + (ttl * 1000L));
                return count;
            }
            log.error("Fallo fail-closed controlando reenvíos OTP en Redis: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException("Servicio de sesiones OTP no disponible temporalmente", ex);
        }
    }

    private void eliminarClave(String clave) {
        try {
            redisTemplate.delete(clave);
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                localMemoryStore.remove(clave);
                localExpiryStore.remove(clave);
                return;
            }
            log.error("Fallo fail-closed eliminando clave OTP en Redis: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException("Servicio de sesiones OTP no disponible temporalmente", ex);
        }
    }
}
