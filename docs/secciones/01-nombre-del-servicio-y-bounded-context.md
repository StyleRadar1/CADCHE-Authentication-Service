[⬅ Volver al README Principal](../../README.md)

---

## 1. NOMBRE DEL SERVICIO Y SU BOUNDED CONTEXT

* **Identificador de Servicio:** `ms-auth` (`CADCHE-Authentication-Service`)
* **Paquete Arquitectónico:** `Paquete 1: Identidad` · Equipo `CADche`
* **Puerto de Escucha Oficial:** `8100` (bloque `8100` del equipo: ms-auth `8100`, ms-usuarios `8120`, ms-tiendas `8140`)
* **Almacenamiento Asignado:** `Redis 7`. **ms-auth no tiene base de datos relacional** (decisión de arquitectura, ver [§3.2](02-arquitectura-hexagonal-y-diagramas.md#32-diagrama-de-base-de-datos)): las cuentas, contraseñas y el estado `emailVerified` pertenecen a `ms-usuarios` (patrón *Database-per-Service*) y todo el estado propio de ms-auth es efímero y con TTL (códigos OTP, bloqueos, lista negra de JWT, estado OAuth2 y rate limiting).
* **Bounded Context:** `Autenticación, Identidad Federada y Control de Acceso (IAM)`

### Responsabilidades del Dominio

| Qué HACE este microservicio (alcance) | Qué NO HACE (límites) |
| :--- | :--- |
| • **SR-RF-02 · Iniciar sesión:** valida correo y contraseña contra `ms-usuarios`, exige cuenta `ACTIVA` (RN-03) y, si el correo ya está verificado, entrega el JWT de una vez (RN-05). Si no, envía un código OTP de 6 dígitos (FA-13). | • NO guarda usuarios, perfiles ni hashes de contraseña (son de `ms-usuarios`). |
| • **SR-RF-92 · Verificar el correo con OTP:** el código dura `300 s`, admite `3` intentos y se pide **una sola vez por cuenta** (decisión D2). Al tercer fallo anula solo el código y bloquea pedir otro durante `60 s` (D1); la verificación en curso dura `15 min`. Al verificar, marca el correo en `ms-usuarios`. | • NO administra almacenes ni fundaciones (`ms-tiendas`). |
| • **Google OAuth2 / OIDC:** ID Token RS256 validado contra JWKS o *Authorization Code* con `state` anti-CSRF de un solo uso; no pide OTP y marca el correo como verificado (B8). | • NO publica eventos hacia otros servicios. |
| • **SR-RF-113 · Renovar sesión y SR-RF-112 · Cerrar sesión:** renovación con rotación (`/refresh`; el token anterior queda revocado), cierre con lista negra (`/logout`, `204`) y **revocación global** al recibir `user.status.changed` de ms-usuarios (cuenta `INACTIVE`/`BLOCKED`), con idempotencia por `eventId`. | • NO gestiona catálogo, inventario ni pagos. |
| • **Contrato de errores v1.2 (ProblemDetail RFC 9457):** cada error trae `type`/`title`/`detail`/`instance`, `code` del catálogo, `errors`, `attemptsRemaining`, `retryAfterSeconds` y la cabecera `Retry-After` en todos los `429`. | • NO expone el registro real (el `/register` es un stub solo para `dev`/`test`; en QA/PROD responde `503`). |

---

[⬅ Volver al README Principal](../../README.md)
