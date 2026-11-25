[⬅ Volver al README Principal](../../README.md)

---

## 4. EVENTOS QUE PUBLICA Y CONSUME

| Dirección | Exchange / routing key | Cola | Origen → destino | Acción en ms-auth |
| :--- | :--- | :--- | :--- | :--- |
| **CONSUME** | `styleradar.exchange` / `user.status.changed` | `styleradar.auth.user-status-changed.q` (DLQ `styleradar.auth.dlq`) | `ms-usuarios` → `ms-auth` | `RevocarSesionesUsuarioUseCase`: si `newStatus` es `INACTIVE` o `BLOCKED`, guarda `usuario:revocado:{id}` y todos los tokens emitidos antes quedan inválidos. |
| **PUBLICA** | `styleradar.exchange` / `user.session.changed` | La crea cada consumidor | `ms-auth` → `ms-estadisticas` | `SesionesActivasService`: +1 al entregar un token (login directo, OTP verificado o Google); -1 al cerrar sesión, al vencer el token o al revocarse la cuenta. Renovar no publica. |

* **Idempotencia:** un `eventId` repetido se descarta (`evento:procesado:{eventId}`, 24 h).
* **Resiliencia:** 3 reintentos con *backoff* exponencial y luego DLQ. Si Redis no responde, el consumidor falla para que el mensaje se reintente y nunca se pierda una revocación.
* **Formato:** el del análisis v2 de ms-usuarios (`eventId`, `userId`, `previousStatus`, `newStatus`, `changedBy`, `reason`, `changedAt`). Mientras ms-usuarios migra, también se aceptan los nombres anteriores en español (`eventoId`, `usuarioId`, `estadoNuevo`…) y los estados `ACTIVO`/`BLOQUEADO`.

```json
{ "eventId": "evt-123", "userId": 42, "previousStatus": "ACTIVE", "newStatus": "BLOCKED",
  "changedBy": 1, "reason": "Fraude", "changedAt": "2026-11-13T10:15:30Z" }
```

**`user.session.changed`.** ms-auth guarda en Redis las sesiones abiertas (`sesion:{jti}`, `sesiones:vencimiento` y `sesiones:usuario:{id}`) para saber cuándo termina cada una. Un barrido cada minuto (`ExpiracionSesionesScheduler`) cierra las de tokens vencidos, así que el -1 de una expiración llega hasta un minuto después. Quitar el `jti` del sorted set es atómico, de modo que cada sesión produce un solo -1 aunque haya varias instancias. El envío va en un hilo aparte: un broker lento nunca demora el login, y sin `RABBITMQ_PASSWORD` no se publica.

```json
{ "eventId": "6f1c...", "eventType": "USER_SESSION_CHANGED", "userId": 12, "sessionId": "b7e4...",
  "action": "LOGIN", "delta": 1, "occurredAt": "2026-11-25T15:04:05Z" }
```

`action` toma `LOGIN` (+1), `LOGOUT`, `EXPIRED` o `REVOKED` (-1). `sessionId` se conserva al renovar el token.

---

[⬅ Volver al README Principal](../../README.md)
