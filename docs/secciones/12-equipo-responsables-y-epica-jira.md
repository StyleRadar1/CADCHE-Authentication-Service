[⬅ Volver al README Principal](../../README.md)

---

## 12. EQUIPO, RESPONSABLES Y ENLACE A LA ÉPICA EN JIRA

### Equipo y Responsables del Microservicio

Desarrollado y mantenido por el **Squad `CADche` (Estación CADche · Paquete 1: Identidad)** para la asignatura DOSW 2026.

<div align="center">

| Rol | Nombre | Usuario GitHub |
| :---: | :---: | :---: |
| **Líder del equipo** | `Juan Diego Valderrama` | [@juandivg26](https://github.com/juandivg26) |
| **Backend Developer (ms-auth, ms-tiendas)** | `Juan Diego Gaitán` | [@mamagege](https://github.com/mamagege) |
| **Backend Developer (ms-usuarios, ms-auth)** | `Julián Camilo Giral` | [@jcobos548](https://github.com/jcobos548) |

</div>

El seguimiento de requerimientos, historias de usuario, evidencias y artefactos de entrega se centraliza en los siguientes enlaces oficiales:

### Enlaces de Gestión, Repositorios y Evidencias

* **Épica Oficial del Microservicio (Jira):** [https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog](https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog)
* **Tablero Scrum del Squad CADche (Jira):** [https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog](https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog)
* **Documento de Arquitectura Oficial (Libro interactivo StyleRadar · 17 vistas: Portada, Sumario, 01–14 y Contraportada):** [`docs/DocumentoTecnico.html`](../DocumentoTecnico.html)
* **Health Check en Producción (`ms-auth` PROD):** [https://styleradar-auth-prod.azurewebsites.net/actuator/health](https://styleradar-auth-prod.azurewebsites.net/actuator/health) *(Swagger UI deshabilitado en `prod`)*
* **API Swagger en Calidad (`ms-auth` QA):** [https://styleradar-auth-qa.azurewebsites.net/swagger-ui.html](https://styleradar-auth-qa.azurewebsites.net/swagger-ui.html) ([`/v3/api-docs`](https://styleradar-auth-qa.azurewebsites.net/v3/api-docs))
* **Pipelines GitHub Actions y Reportes JaCoCo:** [StyleRadar1/CADCHE-Authentication-Service/actions](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions)
* **Calidad Estática en SonarQube Cloud:** [StyleRadar1_CADCHE-Authentication-Service](https://sonarcloud.io/project/overview?id=StyleRadar1_CADCHE-Authentication-Service)

### Historia de Usuario Implementada: Autenticación Dual (OTP + Google OAuth2 / OIDC) y Swagger

> **HU · `CADCHE · ms-auth | Autenticación dual con OTP clásico y Google OAuth2 / OpenID Connect funcional en Swagger`**
>
> * **Como** usuario de StyleRadar (`BUYER`, `STORE_ADMIN`, `FOUNDATION_REPRESENTATIVE` o `STYLERADAR_ADMIN`)
> * **Quiero** poder iniciar sesión tanto con mi correo + contraseña + código OTP de 6 dígitos como con mi cuenta de Google (`Sign-In` ID Token o flujo `Authorization Code` ejecutable desde Swagger UI y Frontend)
> * **Para** obtener un token JWT Bearer oficial de StyleRadar de forma segura, auditable y compatible con los controles OWASP Top 10:2025.

| ID | Criterio de Aceptación (Verificado en Código y Pruebas) | Evidencia Automática |
| :---: | :--- | :--- |
| **CA-01** | El acceso tradicional (`POST /api/v1/auth/login`) delega la validación en `ms-usuarios`, almacena el OTP salteado en SHA-256 (`300 s`, máx `3` intentos/reenvíos), aplica rollback si falla SMTP y emite JWT `HS256` en `/otp/verify` (`401` si inválido/expirado, `429` si agotado). | `AuthControllerTest`, `OtpServiceTest`, `EmailServiceTest` |
| **CA-02** | El acceso directo (`POST /api/v1/auth/oauth2/google`) verifica firma `RS256` contra Google JWKS (`NimbusJwtDecoder`), emisor Google, audiencia `GOOGLE_CLIENT_ID`, vigencia y `email_verified=true`; resuelve/aprovisiona en `ms-usuarios` (`/internal/api/v1/usuarios/oauth2`), valida `ACTIVA` y emite JWT sin desafío OTP adicional. | `GoogleOAuthAdapterTest`, `GoogleOAuth2ServiceTest` |
| **CA-03** | El flujo navegable desde Swagger UI (`GET /oauth2/google/url` &rarr; Consentimiento Google &rarr; `GET /callback` o `POST /code`) protege contra CSRF con `oauth2:state:{state}` de un solo uso (`getAndDelete`, TTL `300 s`) y restringe redirecciones con `GOOGLE_ALLOWED_REDIRECT_URIS` (anti-Open Redirect). | `GoogleOAuth2ControllerTest`, `GoogleOAuth2SwaggerIntegrationTest` |
| **CA-04** | Ambos métodos comparten un único ciclo JWT: rotación en `/refresh`, revocación en `/logout` (`blacklist:*`), revocación administrativa desde RabbitMQ (`user.status.changed` -> `usuario:revocado:*`), rate limiting (`429`) y respuestas homogéneas `ErrorResponseDTO` (`503` fail-closed en `qa`/`prod`). | `JwtAuthenticationFilterTest`, `RateLimitingFilterTest`, `OwaspTop10SecurityTest`, `UsuarioEstadoCambiadoConsumerTest` |

### Requerimientos Funcionales y No Funcionales Asignados (análisis de requerimientos v6)

| Código RF | Nombre del Requerimiento | Actor Principal | Criterio de Aceptación Clave Verificado |
| :---: | :--- | :--- | :--- |
| `SR-RF-02` | Iniciar sesión | Usuario comprador, Administrador de almacén, Representante de fundación aliada y Administrador StyleRadar | Valida credenciales contra `ms-usuarios`; cuenta activa (RN-03); si el correo está verificado entrega el JWT de una vez, si no envía un código OTP (FA-13); ingreso con Google sin código; 15 intentos por minuto (`429` con `Retry-After`). |
| `SR-RF-92` | Verificar el correo con código OTP | Los cuatro actores | Código de 6 dígitos, `300 s` y `3` intentos; tras 3 fallos, espera de `60 s`; máximo 3 códigos nuevos; verificación en curso de `15 min`; se guarda solo una huella del código. |
| `SR-RF-112` | Cerrar sesión | Los cuatro actores | `204` sin cuerpo; el token queda revocado hasta su vencimiento; `401 INVALID_TOKEN` sin token o sin `Bearer`, con token vacío o mal formado, o con token inválido o vencido. |
| `SR-RF-113` | Renovar sesión | Los cuatro actores | `200` con un token nuevo que conserva la cuenta y el rol; el anterior queda revocado (rotación); no renueva tokens revocados ni de cuentas bloqueadas después de emitirlos; `refreshToken` de máximo 2048 caracteres. |
| `SR-RF-AUTH-01` | Revocar sesiones al inactivar o bloquear (fuera del análisis v6) | Sistema (evento de `ms-usuarios`) | Se conserva porque lo usa RN-04 de SR-RF-113: al recibir `user.status.changed` con `INACTIVE` o `BLOCKED` invalida los tokens emitidos antes, con idempotencia por `eventId`. |

Cada flujo alterno tiene su prueba en `TrazabilidadRequerimientosAuthTest` (clase anidada `AnalisisV6` para lo nuevo de la v6).

---: | :--- | :--- | :--- |
| `SR-RF-01` | Contrato de registro inicial de comprador | `BUYER` | Valida Bean Validation (`nombre`, `email`, password fuerte, `aceptaHabeasData`); opera como stub (`id=12L`, `201`) en `dev`/`test` y bloquea *fail-closed* (`503`) en `qa`/`prod` mientras la entidad reside en `ms-usuarios`. |
| `SR-RF-02` | Inicio de sesión multi-rol con 2FA OTP y renovación JWT | `BUYER`, `STORE_ADMIN`, `FOUNDATION_REPRESENTATIVE`, `STYLERADAR_ADMIN` | Valida credenciales contra `ms-usuarios`, exige segundo factor OTP (`300 s`) y emite `LoginResponseDTO` con JWT `HS256` (`PT24H`/`12H`/`8H`), soportando rotación en `/refresh`. |
| `SR-RF-03` | Autenticación federada con Google OAuth2 / OpenID Connect | `BUYER`, `STORE_ADMIN`, `FOUNDATION_REPRESENTATIVE`, `STYLERADAR_ADMIN` | Soporta ID Token `RS256`/JWKS (`POST /oauth2/google`) y Authorization Code con `state` anti-CSRF (`GET /url`, `GET /callback`, `POST /code`), exigiendo `email_verified=true` y cuenta `ACTIVA` en `ms-usuarios`. |
| `SR-RF-92` | Verificación y reenvío seguro de código OTP de 6 dígitos | `BUYER`, `STORE_ADMIN`, `FOUNDATION_REPRESENTATIVE`, `STYLERADAR_ADMIN` | Almacena hash SHA-256 salteado con `TTL 300 s`, bloquea tras `3` intentos fallidos o `3` reenvíos (`429 Too Many Requests`) y limpia OTP si falla SMTP. |
| `SR-RNF-01` | RBAC, revocación en logout, AMQP y rate limiting | Usuario Autenticado | Protege `/logout` con `Bearer JWT`, registra token en `blacklist:*`, revoca sesiones por evento RabbitMQ (`usuario:revocado:*`) y aplica `RateLimitingFilter` (`15/10` req/min). |
| `SR-RNF-02` | Habeas Data, OpenAPI 3 y endurecimiento OWASP Top 10:2025 | Auditoría / Sistema | Valida `aceptaHabeasData`, publica contratos en Swagger (`dev`/`qa`, off en `prod`), inyecta cabeceras defensivas (`CSP`/`HSTS`/`DENY`) y garantiza modo *fail-closed* (`503`). |

---

[⬅ Volver al README Principal](../../README.md)
