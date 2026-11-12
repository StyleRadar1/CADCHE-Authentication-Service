# ==============================================================================
# STYLE RADAR - PRODUCTION-GRADE MULTI-STAGE DOCKERFILE
# Microservicio: CADCHE-Authentication-Service (ms-auth :8100)
# Estándares: OCI, CIS Benchmark, Non-Root User, JVM Container-Aware
# ==============================================================================

# Etapa 1: Construcción segura de la aplicación
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests -q

# Etapa 2: Imagen Final Ultraligera y Endurecida (Runtime Environment)
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="StyleRadar CADCHE Authentication Service" \
      org.opencontainers.image.description="Microservicio de Autenticación JWT + OTP 2FA (Arquitectura Hexagonal)" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="StyleRadar Team CADche" \
      org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app

# Seguridad: Usuario sin privilegios (CIS Docker Benchmark - Non-Root User)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Certificado público CA del broker RabbitMQ QA (AMQPS TLS :5671)
COPY rabbitmq-ca.pem /tmp/rabbitmq-ca.pem
RUN keytool -importcert -trustcacerts -cacerts -storepass changeit -noprompt -alias styleradar-rabbitmq-ca -file /tmp/rabbitmq-ca.pem && rm -f /tmp/rabbitmq-ca.pem

COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

ENV SERVER_PORT=8100 \
    PORT=8100 \
    WEBSITES_PORT=8100 \
    TZ=America/Bogota

EXPOSE 8100

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8100/actuator/health || exit 1

USER appuser:appgroup

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
