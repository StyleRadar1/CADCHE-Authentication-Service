[⬅ Volver al README Principal](../../README.md)

---

## 7. VARIABLES DE ENTORNO

Plantilla completa en [`.env.example`](../../.env.example). Los secretos nunca van al repositorio: en Azure se configuran como *App Settings*.

| Variable | Descripción | Valor por defecto |
| :--- | :--- | :--- |
| `SERVER_PORT` / `PORT` | Puerto HTTP (Azure inyecta `PORT`) | `8100` |
| `SPRING_PROFILES_ACTIVE` | `dev`, `qa` o `prod` | `dev` |
| `JWT_SECRET_BASE64` (prod) · `JWT_SECRET_QA_BASE64` (qa) · `JWT_SECRET_DEV_BASE64` (dev) | Secreto HMAC Base64 de ≥ 256 bits. **Obligatorio en QA y PROD, sin valor por defecto**; se rechazan los secretos de ejemplo. Debe ser **el mismo** que usa ms-tiendas (`JWT_SECRET_BASE64`). | Solo dev tiene un valor de ejemplo |
| `JWT_DURATION` | Vigencia del token (ISO-8601); alimenta `expiresIn` | `PT24H` |
| `OTP_LOCKOUT_SECONDS` / `OTP_SESSION_TTL_SECONDS` | Espera tras 3 fallos y vida de la verificación en curso | `60` / `900` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL_ENABLED` | Conexión a Redis 7 | `localhost` / `6379` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` (o `RABBITMQ_USER`) / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | Broker del equipo Guatoque Jiraguas | `localhost` / `5672` / `guest` |
| `RABBITMQ_SSL_ENABLED` / `RABBITMQ_SSL_VERIFY_HOSTNAME` | TLS hacia el broker (obligatorio en QA y PROD) | `true` / `true` en qa y prod |
| `USUARIOS_SERVICE_URL` · `USUARIOS_DEV_SERVICE_URL` (dev) · `USUARIOS_QA_SERVICE_URL` (qa) | URL de `ms-usuarios` | `http://localhost:8120` en dev |
| `USUARIOS_DEV_FALLBACK_ENABLED` | Usuario simulado solo para desarrollo sin ms-usuarios; ignorado fuera de `dev` | `false` |
| `USUARIOS_INTERNAL_USERNAME` / `USUARIOS_INTERNAL_PASSWORD` | Credenciales Basic internas hacia ms-usuarios | `ms-auth` / — |
| `USUARIOS_CREDENCIALES_PATH` / `USUARIOS_OAUTH2_PATH` / `USUARIOS_CORREO_VERIFICADO_PATH` | Rutas internas de ms-usuarios en inglés (análisis v2) para validar credenciales, cuentas Google y marcar el correo verificado | `/api/v1/internal/users/verify-credentials` · `/api/v1/internal/users/oauth2` · `/api/v1/internal/users/{id}/email-verification` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_REDIRECT_URI` / `GOOGLE_ALLOWED_REDIRECT_URIS` | Google OAuth2 (lista blanca anti Open Redirect) | — |
| `GOOGLE_STATE_TTL_SECONDS` | Vida del `state` anti-CSRF de un solo uso | `300` |
| `GOOGLE_AUTHORIZATION_URI` / `GOOGLE_TOKEN_URI` / `GOOGLE_JWK_SET_URI` | Endpoints de Google (validados contra SSRF; HTTPS obligatorio en prod) | Endpoints oficiales de Google |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP para los códigos OTP | `smtp.gmail.com` / `587` |
| `MAIL_DEV_CONSOLE_FALLBACK_ENABLED` | Imprime el OTP en consola solo en `dev` cuando no hay SMTP | `false` |
| `SWAGGER_ENABLED` | Publica Swagger UI y `/v3/api-docs` | `true` |
| `CORS_DEV_ALLOWED_ORIGINS` · `CORS_QA_ALLOWED_ORIGINS` · `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (dev incluye `localhost:5173` y `:3000`) | Ver YAML |
| `RATE_LIMIT_ENABLED` / `RATE_LIMIT_MAX_REQUESTS` / `RATE_LIMIT_WINDOW_SECONDS` | Limitador de tasa en Redis | `true` / `15` / `60` |

---

[⬅ Volver al README Principal](../../README.md)
