[⬅ Volver al README Principal](../../README.md)

---

## 6. CÓMO EJECUTAR LOCAL Y CON DOCKER

### Prerrequisitos

* JDK 21 (`java -version`), Maven 3.9+ y Docker 24+.

### Ejecución local con Maven (perfil `dev`)

```bash
git clone https://github.com/StyleRadar1/CADCHE-Authentication-Service.git
cd CADCHE-Authentication-Service
cp .env.example .env            # opcional: el perfil dev lo carga automáticamente
docker compose up -d auth-redis # Redis 7 local
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### Ejecución contenerizada

El pipeline compila y prueba el JAR una sola vez y la imagen reutiliza ese JAR validado, por eso hay que empaquetar antes de construir:

```bash
mvn -B -DskipTests package
docker compose up -d --build    # ms-auth :8100 + auth-redis :6379
docker compose ps
```

### Comprobación de salud

```bash
curl http://localhost:8100/actuator/health
# Swagger: http://localhost:8100/swagger-ui.html
```

---

[⬅ Volver al README Principal](../../README.md)
