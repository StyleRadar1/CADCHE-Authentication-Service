# Análisis de Seguridad y Mitigación OWASP Top 10:2025 · `ms-auth`

[⬅ Volver al README Principal](../../README.md)

---

Evaluación técnica y verificación automatizada de **`CADCHE-Authentication-Service` (`ms-auth`)** frente a las **10 categorías oficiales del estándar OWASP Top 10:2025** (`A01:2025` – `A10:2025`), contrastada directamente contra el código endurecido, la configuración por perfiles (`dev`, `test`, `qa`, `prod`), el contenedor `Dockerfile` y el pipeline `.github/workflows/ci.yml`.

> [!NOTE]
> **Verificación automatizada post-remediación y Google OAuth2 / OIDC:** Suite ejecutada sobre **Java 21 LTS** y **Spring Boot 3.2.12** mediante `mvn -B clean verify com.github.spotbugs:spotbugs-maven-plugin:4.8.6.6:check --no-transfer-progress`: **23 clases de prueba (`src/test/java`)**, **205 pruebas ejecutadas (0 fallos, 0 errores, 0 omitidas)** incluyendo [`OwaspTop10SecurityTest`](../../src/test/java/edu/dosw/styleradar/auth/infrastructure/security/OwaspTop10SecurityTest.java), [`GoogleOAuth2ServiceTest`](../../src/test/java/edu/dosw/styleradar/auth/application/service/GoogleOAuth2ServiceTest.java), [`GoogleOAuthAdapterTest`](../../src/test/java/edu/dosw/styleradar/auth/infrastructure/adapter/out/external/GoogleOAuthAdapterTest.java), [`GoogleOAuth2ControllerTest`](../../src/test/java/edu/dosw/styleradar/auth/infrastructure/adapter/in/rest/GoogleOAuth2ControllerTest.java) y [`GoogleOAuth2SwaggerIntegrationTest`](../../src/test/java/edu/dosw/styleradar/auth/infrastructure/GoogleOAuth2SwaggerIntegrationTest.java); cobertura **JaCoCo `0.8.12`** de **94.8 % en instrucciones (`6359 / 6706`, mínimo 85 %)**, **SpotBugs `0` bugs / `0` errores** y generación de **SBOM CycloneDX 1.6 (`target/bom.json` / `target/bom.xml`, 99 componentes)**. *(ms-auth no usa base SQL: el estado vive en Redis 7 y las credenciales en ms-usuarios).*

### 14.1 Matriz Consolidada OWASP Top 10:2025

| Identificador | Categoría Oficial (OWASP Top 10:2025) | Riesgo | ¿Aplica? | Estado | Controles Técnicos Verificados en `ms-auth` |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **A01:2025** | **Broken Access Control** *(incluye SSRF y Open Redirect)* | Crítico | **SÍ** | 🟢 **PASS *(Mitigado)*** | `/api/v1/auth/logout` exige JWT autenticado; `JwtAuthenticationFilter` verifica `blacklist:<token>` y `usuario:revocado:<id>`; `SecuritySanitizer.validateExternalServiceUrl()` bloquea esquemas no HTTP(S), credenciales embebidas y metadata SSRF en `USUARIOS_SERVICE_URL` y URLs Google OAuth2; `GoogleOAuth2ServiceImpl` valida lista blanca `allowed-redirect-uris` contra Open Redirect. |
| **A02:2025** | **Security Misconfiguration** | Alto | **SÍ** | 🟢 **PASS *(Mitigado)*** | `SecurityHeadersFilter` (`nosniff`, `DENY`, `HSTS`, `CSP`, `Referrer-Policy`, `Permissions-Policy`, `no-store` en `/api/**`); CORS inyectado desde `${styleradar.cors.allowed-origins}` rechazando `*`; perfiles `qa`/`prod` con `show-sql: false`, SSL en PostgreSQL/Redis/RabbitMQ, Swagger deshabilitado en `prod` y `UserDetailsServiceAutoConfiguration` excluida. |
| **A03:2025** | **Software Supply Chain Failures** | Alto | **SÍ** | 🟢 **PASS *(Mitigado)*** | Actualizado a **Java 21 LTS** en `pom.xml`, `Dockerfile` y CI; `Dockerfile` multi-stage con usuario no-root `appuser:appgroup`, etiquetas OCI y `HEALTHCHECK`; `cyclonedx-maven-plugin:2.9.3` genera SBOM CycloneDX 1.6 subido en CI; Actions fijadas por SHA, Buildx `provenance: true`/`sbom: true`, `SpotBugs` (`0` bugs) y SonarCloud Quality Gate. |
| **A04:2025** | **Cryptographic Failures** | Crítico | **SÍ** | 🟢 **PASS *(Mitigado)*** | `JwtServiceImpl` firma `HS256` exigiendo clave Base64 `>= 256` bits y rechaza el secreto de ejemplo en `qa`/`prod`; `GoogleOAuthAdapter` valida firma RS256 de ID Tokens Google contra JWKS (`NimbusJwtDecoder`); `OtpServiceImpl` genera OTP con `SecureRandom` y almacena hash SHA-256 salteado de 16 bytes (`MessageDigest.isEqual`); `GoogleOAuth2ServiceImpl` genera `state` de 32 bytes con `SecureRandom`; TLS en SMTP, DB, Redis y RabbitMQ. |
| **A05:2025** | **Injection** | Alto | **SÍ** | 🟢 **PASS *(Mitigado)*** | `@Valid` con regex de nombre seguro, contraseña fuerte (`8–72` chars con mayúscula, minúscula, dígito y símbolo), longitud de correo/token y `^\d{6}$`; `SecuritySanitizer.normalizeEmail()` rechaza `\r\n\t`; `sanitizeForLog()` evita CRLF Injection; `RabbitMQConfig` restringe `setTrustedPackages`. |
| **A06:2025** | **Insecure Design** | Crítico | **SÍ** | 🟢 **PASS *(Mitigado)*** | `RateLimitingFilter` distribuido en Redis con `429` + `Retry-After` en OTP y `/oauth2/google/**`; tope `maxResends = 3`; tokens `oauth2:state:{state}` consumidos atómicamente de un solo uso (`getAndDelete`) y vinculados al `redirectUri`; rotación en `/refresh`; fallback aislado a `dev` puro; stub `/register` bloqueado (`503`) fuera de `dev`/`test`. |
| **A07:2025** | **Authentication Failures** | Crítico | **SÍ** | 🟢 **PASS *(Mitigado)*** | Doble modalidad autenticada: 2FA clásico (`ms-users` -> OTP hash SHA-256 -> JWT `HS256`) y Google OAuth2 / OIDC (`iss` Google, `aud` StyleRadar, `exp`, `email_verified=true` -> `UsuarioClient.resolverUsuarioOAuth` -> estado `ACTIVA`); `/logout` autenticado con revocación en Redis y eventos RabbitMQ. |
| **A08:2025** | **Software or Data Integrity Failures** | Alto | **SÍ** | 🟢 **PASS *(Mitigado)*** | Firma `HS256` verificada contra alteraciones; caché local RAM deshabilitada en `qa`/`prod` (`fail-closed`); `UsuarioEstadoCambiadoConsumer` filtra `BLOQUEADA`/`INACTIVA`, valida campos nulos y relanza `DataAccessException`; `DefaultJackson2JavaTypeMapper` acotado y `provenance`/`sbom` en Buildx. |
| **A09:2025** | **Security Logging and Alerting Failures** | Medio-Alto | **SÍ** | 🟢 **PASS *(Mitigado)*** | `SecuritySanitizer.maskEmail()` oculta PII en logs; `EmailServiceImpl` suprime códigos OTP fuera de `dev` puro; trazas de auditoría SLF4J en bloqueos `401`/`403`/`429`, rotaciones, revocaciones globales y fallos externos `503` con sanitización anti-CRLF. |
| **A10:2025** | **Mishandling of Exceptional Conditions** | Alto | **SÍ** | 🟢 **PASS *(Mitigado)*** | Modo estricto **fail-closed (`503`)** vía `ServicioExternoNoDisponibleException` ante indisponibilidad de `ms-users`, Redis o SMTP fuera de `dev`; rollback de OTP en fallos SMTP; `GlobalExceptionHandler` sin stack traces en `500` ni detalles internos en `503`, cubierto por `GlobalExceptionHandlerTest`. |

---

### 14.2 Evaluación Detallada por Categoría

#### A01:2025 – Broken Access Control *(incluye SSRF y Open Redirect)*

* **¿Aplica?:** **SÍ (Crítico).** Valida tokens JWT con roles en peticiones entrantes, realiza llamadas HTTP salientes hacia `ms-users` y Google OIDC, y valida URIs de callback.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. `SecurityConfig` opera en modo `STATELESS`, habilita `@EnableMethodSecurity`, protege `anyRequest().authenticated()` y excluye `POST /api/v1/auth/logout` de `permitAll()`, exigiendo portador JWT válido.
  2. `JwtAuthenticationFilter` valida firma y expiración, exige `subject` e `issuedAt`, consulta `otpService.esTokenInvalido(token)` (`blacklist:<token>`) y `usuario:revocado:<usuarioId>`, ignora roles desconocidos al construir autoridades `ROLE_*` y devuelve `401` JSON vía `SecurityErrorResponseWriter`.
  3. **Mitigación SSRF & Open Redirect:** `SecuritySanitizer.validateExternalServiceUrl()` (usado por `UsuarioClient`, `GoogleOAuthAdapter` y `GoogleOAuth2ServiceImpl`) valida sintácticamente `USUARIOS_SERVICE_URL` y URIs de Google (`TOKEN_URI`, `JWK_SET_URI`, `AUTHORIZATION_URI`), restringe esquemas a `http`/`https`, bloquea `userInfo`, hosts vacíos o metadata cloud (`169.254.*`, `metadata.google.internal`) y exige `https` en perfil `prod`; adicionalmente `GoogleOAuth2ServiceImpl` valida `allowedRedirectUris` antes de generar el `state`.

#### A02:2025 – Security Misconfiguration

* **¿Aplica?:** **SÍ (Alto).** Afecta cabeceras HTTP, política CORS, Actuator, Swagger, perfiles Spring y contenedor Docker.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. `SecurityHeadersFilter` (`@Order(Ordered.HIGHEST_PRECEDENCE)`) añade `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload`, `Content-Security-Policy`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` y `Cache-Control: no-store, no-cache, must-revalidate` / `Pragma: no-cache` en rutas `/api/**`.
  2. `SecurityConfig` lee orígenes desde `${styleradar.cors.allowed-origins}`, rechaza configuraciones vacías o con comodín `'*'` y limita métodos y cabeceras HTTP permitidas.
  3. `application.yml` desactiva `show-sql`, oculta detalles en `server.error.*` y establece en `false` todas las banderas de fallback local y stub; `application-qa.yml` y `application-prod.yml` fuerzan TLS en Redis y RabbitMQ, deshabilitan `springdoc` en `prod` y limitan Actuator (`health`); `AuthApplication` excluye `UserDetailsServiceAutoConfiguration`.

#### A03:2025 – Software Supply Chain Failures

* **¿Aplica?:** **SÍ (Alto).** Depende de versión de JVM, dependencias Maven, imagen Docker y flujo de CI/CD.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. Runtime y compilación unificados a **Java 21 LTS** en `pom.xml`, `Dockerfile` y `.github/workflows/ci.yml`, junto con dependencias fijadas (`Spring Boot 3.2.12`, `jjwt 0.12.6`, `postgresql 42.7.4`, `flyway 10.20.1`).
  2. `Dockerfile` multi-stage construye con `maven:3.9.9-eclipse-temurin-21-alpine` y ejecuta sobre `eclipse-temurin:21-jre-alpine` con usuario sin privilegios `appuser:appgroup`, etiquetas OCI, flags JVM conscientes de contenedor y `HEALTHCHECK` contra `/actuator/health`.
  3. `cyclonedx-maven-plugin:2.9.3` genera en fase `package` el SBOM CycloneDX 1.6 (`target/bom.xml` y `target/bom.json`, `99` componentes), publicado en CI (`sbom-cyclone-dx-auth`), junto a Actions fijadas por hash SHA, `SpotBugs` (`0` bugs), `JaCoCo` (`94.8 % >= 85 %`), SonarCloud Quality Gate y `provenance: true` / `sbom: true` en Docker Buildx.

#### A04:2025 – Cryptographic Failures

* **¿Aplica?:** **SÍ (Crítico).** Firma tokens JWT de sesión, genera/verifica OTP y protege secretos y transporte.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. `JwtServiceImpl` valida en arranque que `JWT_SECRET_BASE64` sea Base64 válido con mínimo `32` bytes (`256` bits para `HS256`) y prohíbe el uso de la clave de ejemplo de desarrollo (verificada por huella SHA-256 `DISALLOWED_EXAMPLE_FINGERPRINTS`, que incluye los dos secretos de ejemplo publicados, sin literales secretos en código) en perfiles `qa` y `prod`.
  2. `OtpServiceImpl` genera OTPs de 6 dígitos con `java.security.SecureRandom` y persiste únicamente un **hash SHA-256 salteado** (`<saltB64>$<hashB64>`, sal aleatoria de 16 bytes) verificado en tiempo constante con `MessageDigest.isEqual()`, mientras `GoogleOAuth2ServiceImpl` genera `state` criptográfico de 32 bytes con `SecureRandom` y `GoogleOAuthAdapter` verifica firma `RS256` JWKS de Google.
  3. `.env.example` y perfiles `qa`/`prod` parametrizan `JWT_SECRET_BASE64`, `GOOGLE_CLIENT_ID`/`SECRET`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`, `sslmode=require`, Redis SSL, RabbitMQ SSL y SMTP con `starttls.required: true`.

#### A05:2025 – Injection

* **¿Aplica?:** **SÍ (Alto).** Procesa entradas REST, construye claves Redis, redacta correos y emite logs.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. Bean Validation (`@Valid`) estricta en los `9` DTOs de petición: `@Email` con `@Size(max=254)`, `@Size(max=2048)` en `refreshToken`, `@NotBlank` en `idToken`/`code`/`state` de Google OAuth2, regex segura en `nombre`, contraseña robusta en `RegistroAuthRequestDTO` (`^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$`) y `^\d{6}$` en `otpCode`.
  2. `SecuritySanitizer.normalizeEmail()` rechaza caracteres de control (`\r`, `\n`, `\t`, `Cntrl`) antes de indexar Redis o enviar SMTP; `SecuritySanitizer.sanitizeForLog()` sanea URIs y mensajes en filtros, consumidores y `GlobalExceptionHandler`.
  3. `RabbitMQConfig` configura `DefaultJackson2JavaTypeMapper` restringiendo deserialización a paquetes confiables (`edu.dosw.styleradar.auth.*`, `java.util`, `java.time`).

#### A06:2025 – Insecure Design

* **¿Aplica?:** **SÍ (Crítico).** Cubre fuerza bruta sobre login/OTP, reenvíos, Open Redirect OAuth2, rotación de sesiones y stubs.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. `RateLimitingFilter` (`@Order(Ordered.HIGHEST_PRECEDENCE + 5)`) aplica limitación distribuida por IP/ruta en Redis sobre `/login`, `/otp/verify`, `/otp/resend`, `/refresh`, `/register`, `/oauth2/google`, `/oauth2/google/url`, `/oauth2/google/callback` y `/oauth2/google/code`, devolviendo `429` con cabecera `Retry-After`.
  2. `OtpServiceImpl` impone TTL de `300 s`, máx. `3` intentos fallidos y límite de reenvíos (`maxResends = 3` en `otp:reenvios:<email>`) lanzando `OtpIntentosAgotadosException` (`429`) con limpieza de sesión al excederse; `GoogleOAuth2ServiceImpl` valida `allowed-redirect-uris` contra Open Redirect y consume `oauth2:state:{state}` de un solo uso (`getAndDelete`).
  3. `AuthController.refresh()` revoca el token previo al rotar uno nuevo (`invalidarToken`), valida `estaUsuarioRevocado()`, y `AuthController.registrar()` bloquea el stub (`503`) cuando `registration.stub-enabled=false` (activo en `application.yml`, `qa` y `prod`).

#### A07:2025 – Authentication Failures

* **¿Aplica?:** **SÍ (Crítico).** Es la responsabilidad central del microservicio de identidad (`ms-auth`).
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. Doble flujo autenticado: verificación en dos factores (`UsuarioClient` + OTP SHA-256 + JWT `HS256`) y autenticación federada **Google OAuth2 / OIDC** (`RS256`/JWKS + `iss` + `aud` + `exp` + `email_verified=true` + verificación de estado `ACTIVA` en `ms-usuarios`). Cierre de sesión autenticado en `/logout` con bloqueo inmediato en `JwtAuthenticationFilter` (`blacklist:<token>`) y revocación global por cuenta (`usuario:revocado:<usuarioId>`).
  2. Respaldo simulado de `UsuarioClient` restringido exclusivamente a `dev` puro (`SecuritySanitizer.isDevProfileOnly`) con bandera `dev-fallback-enabled=false` por defecto.
  3. Validado mediante `JwtAuthenticationFilterTest`, `AuthControllerTest`, `OtpServiceTest`, `JwtServiceTest`, `GoogleOAuth2ServiceTest`, `GoogleOAuthAdapterTest`, `GoogleOAuth2ControllerTest` y `OwaspTop10SecurityTest`.

#### A08:2025 – Software or Data Integrity Failures

* **¿Aplica?:** **SÍ (Alto).** Abarca tokens JWT, estado compartido en Redis, mensajería RabbitMQ y artefactos CI.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. Verificación estricta de firma HMAC-SHA256 en `JwtServiceImpl` y desactivación del fallback local en RAM fuera de `dev` para evitar divergencia de blacklists entre réplicas.
  2. `UsuarioEstadoCambiadoConsumer` valida eventos/campos nulos, aplica revocación únicamente ante estados `BLOQUEADA` o `INACTIVA` y relanza `DataAccessException` para no perder revocaciones ante errores de Redis; `RabbitMQConfig` fija `defaultRequeueRejected(false)` y paquetes confiables.
  3. `.github/workflows/ci.yml` habilita `provenance: true` y `sbom: true` en Buildx al construir la imagen Docker.

#### A09:2025 – Security Logging and Alerting Failures

* **¿Aplica?:** **SÍ (Medio-Alto).** Registra eventos de seguridad protegiendo PII y secretos temporales (OTP).
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. Enmascaramiento de direcciones electrónicas mediante `SecuritySanitizer.maskEmail()` en `AuthController`, `OtpServiceImpl`, `EmailServiceImpl` y `UsuarioClient`, junto con saneamiento anti-CRLF (`sanitizeForLog`).
  2. `EmailServiceImpl` elimina cualquier impresión de OTP fuera de perfil `dev` explícito.
  3. Trazas de seguridad (`warn`/`error`) en rechazos `401`/`403` (`JwtAuthenticationFilter`, `SecurityConfig`), bloqueos `429` (`RateLimitingFilter`, `OtpServiceImpl`), revocaciones globales (`UsuarioEstadoCambiadoConsumer`) y errores `503`/`500` (`GlobalExceptionHandler`).

#### A10:2025 – Mishandling of Exceptional Conditions

* **¿Aplica?:** **SÍ (Alto).** Garantiza comportamiento seguro *fail-closed* sin fuga de trazas ni estados inconsistentes.
* **Estado:** 🟢 **PASS *(Mitigado)***
* **Controles verificados en código:**
  1. Comportamiento **fail-closed (`503 Service Unavailable`)** mediante `ServicioExternoNoDisponibleException` cuando `ms-users`, Redis o SMTP fallan fuera de `dev` puro (`UsuarioClient`, `OtpServiceImpl`, `EmailServiceImpl`, `JwtAuthenticationFilter`, `RateLimitingFilter`).
  2. Compensación automática en `AuthController.enviarOtpConRollback()` eliminando el OTP de Redis si falla el envío SMTP.
  3. `GlobalExceptionHandler` mapea todas las excepciones de dominio, validación, seguridad (`AuthenticationException`, `AccessDeniedException`) y fallos externos (`503`) manteniendo respuestas opacas en `500` y `503`, verificado por `GlobalExceptionHandlerTest` y `OwaspTop10SecurityTest`.

---

### 14.3 Resumen de Endurecimiento y Evidencia de Pruebas

| Eje de Remediación | Implementación y Cobertura Verificada | Evidencia en Código / Pruebas |
| :--- | :--- | :--- |
| **Control de Acceso, Blacklist, SSRF y Google OAuth2 (`A01`, `A07`)** | Validación de `blacklist:<token>` en filtro, `/logout` protegido, SSRF en URLs externas, lista blanca `redirectUri` y validación RS256/JWKS + `state` de un solo uso para Google OAuth2 / OIDC. | `JwtAuthenticationFilter.java`, `SecurityConfig.java`, `GoogleOAuth2Controller.java`, `GoogleOAuth2ServiceImpl.java`, `GoogleOAuthAdapter.java`, `UsuarioClient.java`, `GoogleOAuthAdapterTest.java` |
| **Criptografía y Diseño Resistente (`A04`, `A05`, `A06`)** | OTP con hash SHA-256 salteado + `MessageDigest.isEqual`, entropía JWT `>= 256` bits, `RateLimitingFilter` Redis (`429`) en OTP y Google OAuth2, tope de reenvíos y rotación en `/refresh`. | `OtpServiceImpl.java`, `JwtServiceImpl.java`, `RateLimitingFilter.java`, `SecuritySanitizer.java`, DTOs, `RateLimitingFilterTest.java`, `GoogleOAuth2ServiceTest.java` |
| **Fail-Closed, Auditoría y Mensajería (`A08`, `A09`, `A10`)** | Excepción `503` unificada fuera de `dev`, rollback OTP en fallo SMTP, máscara PII, filtro `BLOQUEADA`/`INACTIVA` y paquetes confiables RabbitMQ. | `ServicioExternoNoDisponibleException.java`, `EmailServiceImpl.java`, `UsuarioEstadoCambiadoConsumer.java`, `RabbitMQConfig.java`, `GlobalExceptionHandlerTest.java` |
| **Endurecimiento de Infraestructura y Supply Chain (`A02`, `A03`)** | Cabeceras defensivas, perfiles `qa`/`prod`, Java 21 LTS, `Dockerfile` no-root con `HEALTHCHECK`, SBOM CycloneDX 1.6 y `SpotBugs` `0` bugs. | `SecurityHeadersFilter.java`, `application*.yml`, `pom.xml`, `Dockerfile`, `.github/workflows/ci.yml`, `OwaspTop10SecurityTest.java`, `GoogleOAuth2SwaggerIntegrationTest.java` |

*Última actualización del análisis: Remediación integral y autenticación Google OAuth2 / OIDC sobre la rama `feature/arreglos` (`205` pruebas PASS, JaCoCo `94.8 %`, SpotBugs `0` bugs, SBOM CycloneDX `99` componentes).*
