[⬅ Volver al README Principal](../../README.md)

---

## 11. SEGURIDAD Y ANÁLISIS OWASP TOP 10:2025

* **Documento técnico completo de seguridad:** [`../security/SR_CADCHE_Auth_Seguridad_y_OWASP_2025.md`](../security/SR_CADCHE_Auth_Seguridad_y_OWASP_2025.md)

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


---

[⬅ Volver al README Principal](../../README.md)
