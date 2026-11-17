package edu.dosw.styleradar.auth.domain.model;

/**
 * Catálogo único de códigos de error del contrato ms-auth ↔ Frontend v1.2 (sección 1.5).
 * <p>
 * El dominio solo conoce el código y su estado HTTP numérico; la traducción a la respuesta
 * HTTP la hace la infraestructura ({@code GlobalExceptionHandler}). El front elige el texto
 * que muestra a partir del código (decisión D3).
 */
public enum CodigoError {

    DATOS_INVALIDOS(400),
    CREDENCIALES_INCORRECTAS(401),
    OTP_INCORRECTO(401),
    OTP_VENCIDO(401),
    SESION_OTP_INEXISTENTE(401),
    TOKEN_INVALIDO(401),
    CUENTA_NO_ACTIVA(403),
    ACCESO_DENEGADO(403),
    METODO_NO_PERMITIDO(405),
    OTP_INTENTOS_AGOTADOS(429),
    OTP_REENVIO_BLOQUEADO(429),
    REENVIOS_AGOTADOS(429),
    LIMITE_SOLICITUDES(429),
    ERROR_INTERNO(500),
    SERVICIO_NO_DISPONIBLE(503);

    private final int estadoHttp;

    CodigoError(int estadoHttp) {
        this.estadoHttp = estadoHttp;
    }

    public int estadoHttp() {
        return estadoHttp;
    }
}
