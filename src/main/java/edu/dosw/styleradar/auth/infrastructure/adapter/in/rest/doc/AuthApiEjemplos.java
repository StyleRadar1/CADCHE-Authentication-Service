package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc;

/**
 * Ejemplos JSON del contrato ms-auth ↔ Frontend v1.2 usados en la documentación OpenAPI (un solo lugar, DRY).
 */
final class AuthApiEjemplos {

    static final String LOGIN_COMPRADOR = "{\"email\":\"comprador@styleradar.com\",\"password\":\"StyleRadar2026*\"}";
    static final String LOGIN_TIENDA = "{\"email\":\"tienda@almacenaliado.co\",\"password\":\"TiendaRadar2026*\"}";
    static final String LOGIN_FUNDACION = "{\"email\":\"representante@abrigar.org\",\"password\":\"Fundacion2026*\"}";
    static final String LOGIN_ADMIN = "{\"email\":\"admin@styleradar.com\",\"password\":\"AdminRadar2026*\"}";

    static final String SESION_CON_TOKEN = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiJ9.firma\","
            + "\"tokenType\":\"Bearer\",\"expiresIn\":86400,\"usuarioId\":12,\"roles\":[\"COMPRADOR\"],"
            + "\"email\":\"comprador@styleradar.com\",\"correoVerificado\":true,\"requiereOtp\":false,"
            + "\"otpExpiraEnSegundos\":null,\"intentosOtpRestantes\":0,\"verificacionPendiente\":false,"
            + "\"mensaje\":\"Inicio de sesión exitoso\"}";

    static final String SESION_PENDIENTE_OTP = "{\"accessToken\":null,\"tokenType\":\"Bearer\",\"expiresIn\":86400,"
            + "\"usuarioId\":12,\"roles\":[\"COMPRADOR\"],\"email\":\"comprador@styleradar.com\","
            + "\"correoVerificado\":false,\"requiereOtp\":true,\"otpExpiraEnSegundos\":300,"
            + "\"intentosOtpRestantes\":3,\"verificacionPendiente\":true,"
            + "\"mensaje\":\"Código de verificación OTP enviado a su correo electrónico\"}";

    static final String OTP_REENVIADO = "{\"email\":\"comprador@styleradar.com\","
            + "\"mensaje\":\"Se envió un código de verificación de 6 dígitos a su correo registrado\","
            + "\"expiraEnSegundos\":300,\"intentosRestantes\":3,\"reenviosRestantes\":2}";

    static final String REGISTRO = "{\"nombre\":\"Laura Gómez\",\"email\":\"laura.gomez@styleradar.com\","
            + "\"telefono\":\"+57 300 123 4567\",\"password\":\"StyleRadar2026*\",\"aceptaHabeasData\":true}";

    static final String USUARIO_REGISTRADO = "{\"id\":12,\"nombre\":\"Laura Gómez\",\"email\":\"laura.gomez@styleradar.com\","
            + "\"telefono\":\"+57 300 123 4567\",\"fechaRegistro\":\"2026-11-16T01:30:00Z\",\"roles\":[\"COMPRADOR\"]}";

    static final String ERROR_DATOS_INVALIDOS = "{\"type\":\"https://api.styleradar.com/errores/datos-invalidos\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"La solicitud contiene datos inválidos\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Bad Request\",\"codigo\":\"DATOS_INVALIDOS\",\"message\":\"La solicitud contiene datos inválidos\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{\"email\":\"El formato del correo electrónico es inválido\"},\"intentosRestantes\":null,\"esperarSegundos\":null}";
    static final String ERROR_CREDENCIALES = "{\"type\":\"https://api.styleradar.com/errores/credenciales-incorrectas\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El correo o la contraseña son incorrectos\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"codigo\":\"CREDENCIALES_INCORRECTAS\",\"message\":\"El correo o la contraseña son incorrectos\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":null}";
    static final String ERROR_CUENTA_NO_ACTIVA = "{\"type\":\"https://api.styleradar.com/errores/cuenta-no-activa\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"La cuenta se encuentra inactiva o bloqueada\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Forbidden\",\"codigo\":\"CUENTA_NO_ACTIVA\",\"message\":\"La cuenta se encuentra inactiva o bloqueada\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":null}";
    static final String ERROR_REENVIO_BLOQUEADO = "{\"type\":\"https://api.styleradar.com/errores/otp-reenvio-bloqueado\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Debe esperar 42 segundos para solicitar un nuevo código.\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"codigo\":\"OTP_REENVIO_BLOQUEADO\",\"message\":\"Debe esperar 42 segundos para solicitar un nuevo código.\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":42}";
    static final String ERROR_LIMITE = "{\"type\":\"https://api.styleradar.com/errores/limite-solicitudes\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Demasiadas solicitudes en un período corto. Intente nuevamente más tarde.\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"codigo\":\"LIMITE_SOLICITUDES\",\"message\":\"Demasiadas solicitudes en un período corto. Intente nuevamente más tarde.\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":60}";
    static final String ERROR_NO_DISPONIBLE = "{\"type\":\"https://api.styleradar.com/errores/servicio-no-disponible\",\"title\":\"Service Unavailable\",\"status\":503,\"detail\":\"El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Service Unavailable\",\"codigo\":\"SERVICIO_NO_DISPONIBLE\",\"message\":\"El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":null}";
    static final String ERROR_OTP_INCORRECTO = "{\"type\":\"https://api.styleradar.com/errores/otp-incorrecto\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El código OTP es incorrecto. Intentos restantes: 2\",\"instance\":\"/api/v1/auth/otp/verify\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"codigo\":\"OTP_INCORRECTO\",\"message\":\"El código OTP es incorrecto. Intentos restantes: 2\",\"path\":\"/api/v1/auth/otp/verify\",\"validationErrors\":{},\"intentosRestantes\":2,\"esperarSegundos\":null}";
    static final String ERROR_OTP_VENCIDO = "{\"type\":\"https://api.styleradar.com/errores/otp-vencido\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El código OTP expiró o no existe. Debe solicitar uno nuevo.\",\"instance\":\"/api/v1/auth/otp/verify\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"codigo\":\"OTP_VENCIDO\",\"message\":\"El código OTP expiró o no existe. Debe solicitar uno nuevo.\",\"path\":\"/api/v1/auth/otp/verify\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":null}";
    static final String ERROR_OTP_AGOTADO = "{\"type\":\"https://api.styleradar.com/errores/otp-intentos-agotados\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.\",\"instance\":\"/api/v1/auth/otp/verify\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"codigo\":\"OTP_INTENTOS_AGOTADOS\",\"message\":\"Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.\",\"path\":\"/api/v1/auth/otp/verify\",\"validationErrors\":{},\"intentosRestantes\":0,\"esperarSegundos\":60}";
    static final String ERROR_SESION_INEXISTENTE = "{\"type\":\"https://api.styleradar.com/errores/sesion-otp-inexistente\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.\",\"instance\":\"/api/v1/auth/otp/resend\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"codigo\":\"SESION_OTP_INEXISTENTE\",\"message\":\"No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.\",\"path\":\"/api/v1/auth/otp/resend\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":null}";
    static final String ERROR_REENVIOS_AGOTADOS = "{\"type\":\"https://api.styleradar.com/errores/reenvios-agotados\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.\",\"instance\":\"/api/v1/auth/otp/resend\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"codigo\":\"REENVIOS_AGOTADOS\",\"message\":\"Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.\",\"path\":\"/api/v1/auth/otp/resend\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":0}";
    static final String ERROR_TOKEN_INVALIDO = "{\"type\":\"https://api.styleradar.com/errores/token-invalido\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El token presentado es inválido, expiró o fue revocado\",\"instance\":\"/api/v1/auth/refresh\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"codigo\":\"TOKEN_INVALIDO\",\"message\":\"El token presentado es inválido, expiró o fue revocado\",\"path\":\"/api/v1/auth/refresh\",\"validationErrors\":{},\"intentosRestantes\":null,\"esperarSegundos\":null}";

    private AuthApiEjemplos() {
    }

}
