package edu.dosw.styleradar.auth.application.service;

import java.time.Instant;

/**
 * Servicio de aplicación para el código OTP de verificación de correo (SR-RF-92) y la revocación
 * de sesiones respaldadas en Redis 7.
 */
public interface OtpService {

    /**
     * Inicia una verificación y genera un código nuevo. Rechaza la solicitud durante la espera
     * obligatoria tras 3 intentos fallidos ({@code OTP_RESEND_BLOCKED}).
     */
    String generarOtp(String email, Long usuarioId, String rol);

    void verificarOtp(String email, String codigo);

    String reenviarOtp(String email);

    Long obtenerUsuarioId(String email);

    String obtenerRol(String email);

    int obtenerIntentosRestantes(String email);

    int obtenerReenviosRestantes(String email);

    long obtenerSegundosRestantesBloqueo(String email);

    long obtenerVigenciaOtpSegundos();

    int obtenerMaxIntentos();

    void eliminarOtp(String email);

    void invalidarToken(String token, long ttlSegundos);

    boolean esTokenInvalido(String token);

    boolean estaUsuarioRevocado(Long usuarioId, long fechaEmisionMillis);

    void revocarSesionesUsuario(Long usuarioId, Instant fechaCambio);

    /**
     * Indica si el evento ya se procesó con éxito (idempotencia del consumidor AMQP).
     */
    boolean eventoYaProcesado(String eventoId);

    /**
     * Marca el evento como procesado. Se llama solo después de aplicar su efecto.
     * Devuelve {@code false} si ya estaba marcado.
     */
    boolean registrarEventoProcesado(String eventoId);
}
