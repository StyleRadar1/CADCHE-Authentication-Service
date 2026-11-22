package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc;

/**
 * JSON examples of the requirements analysis v2 used in the OpenAPI documentation (single place, DRY).
 */
final class AuthApiEjemplos {

    static final String LOGIN_COMPRADOR = "{\"email\":\"buyer@styleradar.com\",\"password\":\"StyleRadar2026*\"}";
    static final String LOGIN_TIENDA = "{\"email\":\"tienda@almacenaliado.co\",\"password\":\"TiendaRadar2026*\"}";
    static final String LOGIN_FUNDACION = "{\"email\":\"representante@abrigar.org\",\"password\":\"Fundacion2026*\"}";
    static final String LOGIN_ADMIN = "{\"email\":\"admin@styleradar.com\",\"password\":\"AdminRadar2026*\"}";

    static final String SESION_CON_TOKEN = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiJ9.signature\","
            + "\"tokenType\":\"Bearer\",\"expiresIn\":28800,\"userId\":12,\"roles\":[\"BUYER\"],"
            + "\"email\":\"buyer@styleradar.com\",\"emailVerified\":true,\"otpRequired\":false,"
            + "\"otpExpiresInSeconds\":null,\"otpAttemptsRemaining\":0,"
            + "\"message\":\"Inicio de sesión exitoso\"}";

    static final String SESION_PENDIENTE_OTP = "{\"accessToken\":null,\"tokenType\":\"Bearer\",\"expiresIn\":28800,"
            + "\"userId\":12,\"roles\":[\"BUYER\"],\"email\":\"buyer@styleradar.com\","
            + "\"emailVerified\":false,\"otpRequired\":true,\"otpExpiresInSeconds\":300,"
            + "\"otpAttemptsRemaining\":3," 
            + "\"message\":\"Código de verificación OTP enviado a su correo electrónico\"}";

    static final String OTP_REENVIADO = "{\"email\":\"buyer@styleradar.com\","
            + "\"message\":\"Se envió un código de verificación de 6 dígitos a su correo registrado\","
            + "\"expiresInSeconds\":300,\"attemptsRemaining\":3,\"resendsRemaining\":2}";

    static final String REGISTRO = "{\"name\":\"Laura Gómez\",\"email\":\"laura.gomez@styleradar.com\","
            + "\"phone\":\"+57 300 123 4567\",\"password\":\"StyleRadar2026*\",\"dataPolicyAccepted\":true}";

    static final String USUARIO_REGISTRADO = "{\"id\":12,\"name\":\"Laura Gómez\",\"email\":\"laura.gomez@styleradar.com\","
            + "\"phone\":\"+57 300 123 4567\",\"registeredAt\":\"2026-11-16T01:30:00Z\",\"roles\":[\"BUYER\"]}";

    static final String ERROR_DATOS_INVALIDOS = "{\"type\":\"https://api.styleradar.com/errors/invalid-data\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"La solicitud contiene datos inválidos\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Bad Request\",\"code\":\"INVALID_DATA\",\"message\":\"La solicitud contiene datos inválidos\",\"path\":\"/api/v1/auth/login\",\"errors\":{\"email\":\"El formato del correo electrónico es inválido\"},\"attemptsRemaining\":null,\"retryAfterSeconds\":null}";
    static final String ERROR_CREDENCIALES = "{\"type\":\"https://api.styleradar.com/errors/invalid-credentials\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El correo o la contraseña son incorrectos\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"code\":\"INVALID_CREDENTIALS\",\"message\":\"El correo o la contraseña son incorrectos\",\"path\":\"/api/v1/auth/login\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":null}";
    static final String ERROR_CUENTA_NO_ACTIVA = "{\"type\":\"https://api.styleradar.com/errors/account-not-active\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"La cuenta se encuentra inactiva o bloqueada\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Forbidden\",\"code\":\"ACCOUNT_NOT_ACTIVE\",\"message\":\"La cuenta se encuentra inactiva o bloqueada\",\"path\":\"/api/v1/auth/login\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":null}";
    static final String ERROR_REENVIO_BLOQUEADO = "{\"type\":\"https://api.styleradar.com/errors/otp-resend-blocked\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Debe esperar 42 segundos para solicitar un nuevo código.\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"code\":\"OTP_RESEND_BLOCKED\",\"message\":\"Debe esperar 42 segundos para solicitar un nuevo código.\",\"path\":\"/api/v1/auth/login\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":42}";
    static final String ERROR_LIMITE = "{\"type\":\"https://api.styleradar.com/errors/rate-limit-exceeded\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Demasiadas solicitudes en un período corto. Intente nuevamente más tarde.\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Demasiadas solicitudes en un período corto. Intente nuevamente más tarde.\",\"path\":\"/api/v1/auth/login\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":60}";
    static final String ERROR_NO_DISPONIBLE = "{\"type\":\"https://api.styleradar.com/errors/service-unavailable\",\"title\":\"Service Unavailable\",\"status\":503,\"detail\":\"El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.\",\"instance\":\"/api/v1/auth/login\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Service Unavailable\",\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.\",\"path\":\"/api/v1/auth/login\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":null}";
    static final String ERROR_OTP_INCORRECTO = "{\"type\":\"https://api.styleradar.com/errors/otp-incorrect\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El código OTP es incorrecto. Intentos restantes: 2\",\"instance\":\"/api/v1/auth/otp/verify\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"code\":\"OTP_INCORRECT\",\"message\":\"El código OTP es incorrecto. Intentos restantes: 2\",\"path\":\"/api/v1/auth/otp/verify\",\"errors\":{},\"attemptsRemaining\":2,\"retryAfterSeconds\":null}";
    static final String ERROR_OTP_VENCIDO = "{\"type\":\"https://api.styleradar.com/errors/otp-expired\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El código OTP expiró o no existe. Debe solicitar uno nuevo.\",\"instance\":\"/api/v1/auth/otp/verify\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"code\":\"OTP_EXPIRED\",\"message\":\"El código OTP expiró o no existe. Debe solicitar uno nuevo.\",\"path\":\"/api/v1/auth/otp/verify\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":null}";
    static final String ERROR_OTP_AGOTADO = "{\"type\":\"https://api.styleradar.com/errors/otp-attempts-exhausted\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.\",\"instance\":\"/api/v1/auth/otp/verify\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"code\":\"OTP_ATTEMPTS_EXHAUSTED\",\"message\":\"Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.\",\"path\":\"/api/v1/auth/otp/verify\",\"errors\":{},\"attemptsRemaining\":0,\"retryAfterSeconds\":60}";
    static final String ERROR_SESION_INEXISTENTE = "{\"type\":\"https://api.styleradar.com/errors/otp-session-not-found\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.\",\"instance\":\"/api/v1/auth/otp/resend\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"code\":\"OTP_SESSION_NOT_FOUND\",\"message\":\"No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.\",\"path\":\"/api/v1/auth/otp/resend\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":null}";
    static final String ERROR_REENVIOS_AGOTADOS = "{\"type\":\"https://api.styleradar.com/errors/resends-exhausted\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.\",\"instance\":\"/api/v1/auth/otp/resend\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Too Many Requests\",\"code\":\"RESENDS_EXHAUSTED\",\"message\":\"Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.\",\"path\":\"/api/v1/auth/otp/resend\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":0}";
    static final String ERROR_TOKEN_INVALIDO = "{\"type\":\"https://api.styleradar.com/errors/invalid-token\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"El token presentado es inválido, expiró o fue revocado\",\"instance\":\"/api/v1/auth/refresh\",\"timestamp\":\"2026-11-16T10:15:30\",\"error\":\"Unauthorized\",\"code\":\"INVALID_TOKEN\",\"message\":\"El token presentado es inválido, expiró o fue revocado\",\"path\":\"/api/v1/auth/refresh\",\"errors\":{},\"attemptsRemaining\":null,\"retryAfterSeconds\":null}";

    private AuthApiEjemplos() {
    }

}
