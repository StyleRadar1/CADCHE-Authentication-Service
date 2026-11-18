package edu.dosw.styleradar.auth.domain.model;

/**
 * Catálogo único de códigos de error del contrato ms-auth ↔ Frontend v1.2 (sección 1.5).
 * <p>
 * El dominio solo conoce el código y su estado HTTP numérico; la traducción a la respuesta
 * HTTP la hace la infraestructura ({@code GlobalExceptionHandler}). El front elige el texto
 * que muestra a partir del código (decisión D3).
 */
public enum CodigoError {

    INVALID_DATA(400),
    INVALID_CREDENTIALS(401),
    OTP_INCORRECT(401),
    OTP_EXPIRED(401),
    OTP_SESSION_NOT_FOUND(401),
    INVALID_TOKEN(401),
    ACCOUNT_NOT_ACTIVE(403),
    ACCESS_DENIED(403),
    METHOD_NOT_ALLOWED(405),
    OTP_ATTEMPTS_EXHAUSTED(429),
    OTP_RESEND_BLOCKED(429),
    RESENDS_EXHAUSTED(429),
    RATE_LIMIT_EXCEEDED(429),
    INTERNAL_ERROR(500),
    SERVICE_UNAVAILABLE(503);

    private final int estadoHttp;

    CodigoError(int estadoHttp) {
        this.estadoHttp = estadoHttp;
    }

    public int estadoHttp() {
        return estadoHttp;
    }
}
