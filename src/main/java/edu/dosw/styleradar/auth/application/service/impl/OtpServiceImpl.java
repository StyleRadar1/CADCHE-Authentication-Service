package edu.dosw.styleradar.auth.application.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.OtpBloqueadoTemporalmenteException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/**
 * Gestión del código OTP de verificación de correo (SR-RF-92) y de la revocación de sesiones en Redis 7.
 * <p>
 * Claves por correo normalizado:
 * <ul>
 *   <li>{@code otp:{email}} hash SHA-256 salteado del código (vigencia del código, 5 min).</li>
 *   <li>{@code otp:intentos:{email}} intentos que quedan para el código actual (vigencia del código).</li>
 *   <li>{@code otp:usuario:}, {@code otp:rol:}, {@code otp:reenvios:} datos de la verificación en curso (15 min).</li>
 *   <li>{@code otp:bloqueo:{email}} espera obligatoria tras agotar los intentos (60 s, decisión D1).</li>
 * </ul>
 * Opera fail-closed: si Redis no responde se lanza {@link ServicioExternoNoDisponibleException} (HTTP 503),
 * salvo en el perfil {@code dev} con el respaldo en memoria habilitado de forma explícita.
 */
@Service
@Slf4j
public final class OtpServiceImpl implements OtpService {

    private static final String KEY_OTP = "otp:";
    private static final String KEY_INTENTOS = "otp:intentos:";
    private static final String KEY_REENVIOS = "otp:reenvios:";
    private static final String KEY_USUARIO = "otp:usuario:";
    private static final String KEY_ROL = "otp:rol:";
    private static final String KEY_BLOQUEO = "otp:bloqueo:";
    private static final String KEY_BLACKLIST = "blacklist:";
    private static final String KEY_USUARIO_REVOCADO = "usuario:revocado:";
    private static final String KEY_EVENTO_PROCESADO = "evento:procesado:";
    private static final long EVENTO_IDEMPOTENCIA_TTL_SEGUNDOS = TimeUnit.HOURS.toSeconds(24);
    private static final String MENSAJE_REDIS_NO_DISPONIBLE = "Servicio de sesiones OTP no disponible temporalmente";

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

    @Value("${styleradar.security.otp.lockout-seconds:60}")
    private long lockoutSeconds = 60L;

    @Value("${styleradar.security.otp.session-ttl-seconds:900}")
    private long sessionTtlSeconds = 900L;

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
        exigirSinBloqueo(normalizedEmail);

        String codigo = nuevoCodigo();
        guardarCodigoVigente(normalizedEmail, codigo);
        if (usuarioId != null) {
            guardarValor(KEY_USUARIO + normalizedEmail, String.valueOf(usuarioId), sessionTtlSeconds);
        }
        if (rol != null) {
            guardarValor(KEY_ROL + normalizedEmail, rol, sessionTtlSeconds);
        }
        eliminarClave(KEY_REENVIOS + normalizedEmail);

        log.info("Código OTP generado para {}", SecuritySanitizer.maskEmail(normalizedEmail));
        return codigo;
    }

    @Override
    public void verificarOtp(String email, String codigo) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String maskedEmail = SecuritySanitizer.maskEmail(normalizedEmail);

        String hashGuardado = obtenerValor(KEY_OTP + normalizedEmail);
        if (hashGuardado == null) {
            log.warn("Intento de verificación para OTP expirado o inexistente: {}", maskedEmail);
            throw new OtpExpiradoException("El código OTP expiró o no existe. Debe solicitar uno nuevo.");
        }

        if (verificarHashOtp(hashGuardado, codigo)) {
            log.info("Código OTP verificado exitosamente para {}", maskedEmail);
            return;
        }

        Long restantes = decrementarIntentos(KEY_INTENTOS + normalizedEmail);
        log.warn("Código OTP incorrecto para {}. Intentos restantes: {}", maskedEmail, restantes);

        if (restantes == null || restantes <= 0) {
            anularCodigo(normalizedEmail);
            activarBloqueo(normalizedEmail);
            log.warn("Se agotaron los intentos OTP para {}. Espera obligatoria de {}s activada.",
                    maskedEmail, lockoutSeconds);
            throw OtpIntentosAgotadosException.intentosAgotados(lockoutSeconds);
        }

        throw new OtpInvalidoException(restantes.intValue());
    }

    @Override
    public String reenviarOtp(String email) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String maskedEmail = SecuritySanitizer.maskEmail(normalizedEmail);
        exigirSinBloqueo(normalizedEmail);

        if (obtenerValor(KEY_USUARIO + normalizedEmail) == null || obtenerValor(KEY_ROL + normalizedEmail) == null) {
            log.warn("Solicitud de reenvío OTP sin verificación en curso para {}", maskedEmail);
            throw OtpExpiradoException.sesionInexistente();
        }

        long reenvios = incrementarContador(KEY_REENVIOS + normalizedEmail, sessionTtlSeconds);
        if (reenvios > maxResends) {
            eliminarOtp(normalizedEmail);
            log.warn("Límite de reenvíos OTP excedido ({}/{}) para {}", reenvios, maxResends, maskedEmail);
            throw OtpIntentosAgotadosException.reenviosAgotados();
        }

        String codigo = nuevoCodigo();
        guardarCodigoVigente(normalizedEmail, codigo);
        log.info("Nuevo código OTP generado ({}/{} reenvíos) para {}", reenvios, maxResends, maskedEmail);
        return codigo;
    }

    @Override
    public Long obtenerUsuarioId(String email) {
        String usuarioId = obtenerValor(KEY_USUARIO + SecuritySanitizer.normalizeEmail(email));
        return usuarioId == null ? null : Long.valueOf(usuarioId);
    }

    @Override
    public String obtenerRol(String email) {
        return obtenerValor(KEY_ROL + SecuritySanitizer.normalizeEmail(email));
    }

    @Override
    public int obtenerIntentosRestantes(String email) {
        String valor = obtenerValor(KEY_INTENTOS + SecuritySanitizer.normalizeEmail(email));
        return valor == null ? 0 : Integer.parseInt(valor);
    }

    @Override
    public int obtenerReenviosRestantes(String email) {
        String valor = obtenerValor(KEY_REENVIOS + SecuritySanitizer.normalizeEmail(email));
        int usados = valor == null ? 0 : Integer.parseInt(valor);
        return Math.max(maxResends - usados, 0);
    }

    @Override
    public long obtenerSegundosRestantesBloqueo(String email) {
        String valor = obtenerValor(KEY_BLOQUEO + SecuritySanitizer.normalizeEmail(email));
        if (valor == null) {
            return 0L;
        }
        try {
            long milisRestantes = Long.parseLong(valor) - System.currentTimeMillis();
            return Math.max(TimeUnit.MILLISECONDS.toSeconds(milisRestantes + 999L), 1L);
        } catch (NumberFormatException ex) {
            log.error("Marca de bloqueo OTP corrupta; se aplica la espera completa de {}s", lockoutSeconds);
            return lockoutSeconds;
        }
    }

    @Override
    public long obtenerVigenciaOtpSegundos() {
        return ttlSeconds;
    }

    @Override
    public int obtenerMaxIntentos() {
        return maxAttempts;
    }

    @Override
    public void eliminarOtp(String email) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        anularCodigo(normalizedEmail);
        eliminarClave(KEY_REENVIOS + normalizedEmail);
        eliminarClave(KEY_USUARIO + normalizedEmail);
        eliminarClave(KEY_ROL + normalizedEmail);
        eliminarClave(KEY_BLOQUEO + normalizedEmail);
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
        return ejecutar("consultando blacklist JWT",
                () -> Boolean.TRUE.equals(redisTemplate.hasKey(clave)),
                () -> obtenerValorLocal(clave) != null);
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

    @Override
    public void revocarSesionesUsuario(Long usuarioId, Instant fechaCambio) {
        String clave = KEY_USUARIO_REVOCADO + usuarioId;
        String valor = String.valueOf(fechaCambio.toEpochMilli());
        ejecutar("registrando revocación global de sesiones",
                () -> {
                    redisTemplate.opsForValue().set(clave, valor);
                    return Boolean.TRUE;
                },
                () -> {
                    localMemoryStore.put(clave, valor);
                    return Boolean.TRUE;
                });
    }

    @Override
    public boolean eventoYaProcesado(String eventoId) {
        return obtenerValor(KEY_EVENTO_PROCESADO + eventoId) != null;
    }

    @Override
    public boolean registrarEventoProcesado(String eventoId) {
        String clave = KEY_EVENTO_PROCESADO + eventoId;
        return ejecutar("registrando idempotencia de evento",
                () -> Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(clave, "1", EVENTO_IDEMPOTENCIA_TTL_SEGUNDOS, TimeUnit.SECONDS)),
                () -> localMemoryStore.putIfAbsent(clave, "1") == null);
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

    private void exigirSinBloqueo(String normalizedEmail) {
        long segundosBloqueo = obtenerSegundosRestantesBloqueo(normalizedEmail);
        if (segundosBloqueo > 0) {
            log.warn("Solicitud de código OTP rechazada durante la espera obligatoria para {}",
                    SecuritySanitizer.maskEmail(normalizedEmail));
            throw new OtpBloqueadoTemporalmenteException(segundosBloqueo);
        }
    }

    private void activarBloqueo(String normalizedEmail) {
        long expiraEn = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(lockoutSeconds);
        guardarValor(KEY_BLOQUEO + normalizedEmail, String.valueOf(expiraEn), lockoutSeconds);
    }

    private String nuevoCodigo() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private void guardarCodigoVigente(String normalizedEmail, String codigo) {
        guardarValor(KEY_OTP + normalizedEmail, hashearOtp(codigo), ttlSeconds);
        guardarValor(KEY_INTENTOS + normalizedEmail, String.valueOf(maxAttempts), ttlSeconds);
    }

    /**
     * Anula solo el código vigente; la verificación en curso (usuario, rol y reenvíos) se conserva
     * para poder pedir otro código al terminar la espera (decisión D1).
     */
    private void anularCodigo(String normalizedEmail) {
        eliminarClave(KEY_OTP + normalizedEmail);
        eliminarClave(KEY_INTENTOS + normalizedEmail);
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

    private void guardarValor(String clave, String valor, long ttl) {
        ejecutar("guardando clave OTP",
                () -> {
                    redisTemplate.opsForValue().set(clave, valor, ttl, TimeUnit.SECONDS);
                    return Boolean.TRUE;
                },
                () -> {
                    localMemoryStore.put(clave, valor);
                    localExpiryStore.put(clave, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttl));
                    return Boolean.TRUE;
                });
    }

    private String obtenerValor(String clave) {
        return ejecutar("leyendo clave OTP",
                () -> redisTemplate.opsForValue().get(clave),
                () -> obtenerValorLocal(clave));
    }

    private Long decrementarIntentos(String clave) {
        return ejecutar("decrementando intentos OTP",
                () -> redisTemplate.opsForValue().decrement(clave),
                () -> {
                    String actual = obtenerValorLocal(clave);
                    long decrementado = (actual != null ? Long.parseLong(actual) : maxAttempts) - 1L;
                    localMemoryStore.put(clave, String.valueOf(decrementado));
                    return decrementado;
                });
    }

    private long incrementarContador(String clave, long ttl) {
        return ejecutar("controlando reenvíos OTP",
                () -> {
                    Long valor = redisTemplate.opsForValue().increment(clave);
                    long count = valor != null ? valor : 1L;
                    if (count == 1L) {
                        redisTemplate.expire(clave, ttl, TimeUnit.SECONDS);
                    }
                    return count;
                },
                () -> {
                    String actual = obtenerValorLocal(clave);
                    long count = (actual != null ? Long.parseLong(actual) : 0L) + 1L;
                    localMemoryStore.put(clave, String.valueOf(count));
                    localExpiryStore.putIfAbsent(clave, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttl));
                    return count;
                });
    }

    private void eliminarClave(String clave) {
        ejecutar("eliminando clave OTP",
                () -> Boolean.TRUE.equals(redisTemplate.delete(clave)),
                () -> {
                    localMemoryStore.remove(clave);
                    localExpiryStore.remove(clave);
                    return Boolean.TRUE;
                });
    }

    /**
     * Ejecuta una operación sobre Redis aplicando la política fail-closed (OWASP A10:2025).
     * El respaldo en memoria solo se usa en el perfil {@code dev} con la propiedad habilitada.
     */
    private <T> T ejecutar(String operacion, Supplier<T> enRedis, Supplier<T> enMemoriaDev) {
        try {
            return enRedis.get();
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                log.warn("Redis no disponible {}; usando respaldo en memoria del perfil dev", operacion);
                return enMemoriaDev.get();
            }
            log.error("Fallo fail-closed {} en Redis: {}", operacion,
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(MENSAJE_REDIS_NO_DISPONIBLE, ex);
        }
    }

    private String obtenerValorLocal(String clave) {
        Long exp = localExpiryStore.get(clave);
        if (exp != null && System.currentTimeMillis() > exp) {
            localMemoryStore.remove(clave);
            localExpiryStore.remove(clave);
            return null;
        }
        return localMemoryStore.get(clave);
    }

    private boolean isDevFallbackActive() {
        if (!localFallbackEnabled) {
            return false;
        }
        return environment == null || SecuritySanitizer.isDevProfileOnly(environment);
    }
}
