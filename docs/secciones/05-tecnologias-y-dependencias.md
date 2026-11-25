[⬅ Volver al README Principal](../../README.md)

---

## 5. TECNOLOGÍAS Y DEPENDENCIAS

| Componente | Tecnología | Versión | Justificación |
| :--- | :--- | :--- | :--- |
| Lenguaje | Java LTS | `21` | Estándar del curso; *records* inmutables para el dominio. |
| Framework | Spring Boot | `3.2.12` | Web, Security, AMQP, Mail, Actuator y Validation. |
| Almacenamiento | Redis | `7` | OTP, bloqueos, lista negra de JWT, estado OAuth2 y rate limiting con TTL nativo. |
| Mensajería | RabbitMQ (Spring AMQP) | `3.13` | Consumo de `UsuarioEstadoCambiadoEvent` con DLQ. |
| Seguridad | Spring Security + JJWT + OAuth2 JOSE | `6.2 / 0.12.6` | JWT `HS256` (secreto Base64 ≥ 256 bits) y validación RS256/JWKS de Google. |
| Mapeo | MapStruct + Lombok | `1.5.5 / 1.18.42` | Mapper de salida generado en compilación. |
| Documentación | springdoc-openapi | `2.3.0` | Swagger UI y OpenAPI 3. |
| Pruebas | JUnit 5, Mockito, Testcontainers | `5.10 / 5.7 / 1.19.8` | Unitarias, WebMvc e integración con **Redis 7 real**. |
| Calidad | JaCoCo, SpotBugs, SonarCloud, CycloneDX | `0.8.12 / 4.8.6 / – / 2.9.3` | Cobertura ≥ 85 %, análisis estático y SBOM. |
| Contenedor | Eclipse Temurin JRE Alpine | `21` | Imagen OCI no-root con `HEALTHCHECK`. |

> **PostgreSQL 16** es el motor del stack para los servicios con datos relacionales (por ejemplo `ms-usuarios` y `ms-tiendas`). Este servicio no lo usa por la decisión de [§3.2](02-arquitectura-hexagonal-y-diagramas.md#32-diagrama-de-base-de-datos).

### Análisis De Tecnologías Del Servicio (Plantilla Maestra Oficial)

Este apartado constituye la transcripción y aplicación integral de la **Plantilla Maestra Oficial de Análisis de Tecnologías** de StyleRadar para el microservicio `ms-auth` (`CADCHE-Authentication-Service`), desarrollada conforme al estándar de entrega de arquitectura de software para DOSW 2026.

#### Ficha Técnica del Documento

* **Proyecto / Asignatura:** StyleRadar · DOSW 2026
* **Microservicio:** `CADCHE-Authentication-Service` (`ms-auth`, Puerto `8100`)
* **Paquete Arquitectónico:** Paquete 1: Identidad
* **Alcance Arquitectónico:** Evaluación multicriterio de 13 Microservicios, API Gateway, OAuth2/OIDC y Arquitectura Orientada a Eventos
* **Equipo Responsable (CADche):** Julian Camilo Giral (`@jcobos548`), Juan Diego Gaitan (`@mamagege`), Juan Nicolás Álvarez, Camilo Ortiz, Daniel Valero, Juan Diego Valderrama, Paula Alejandra Díaz
* **Versión y Fecha:** v2.0 · Noviembre 2026 *(actualizada tras Google OAuth2/OIDC, Java 21 LTS y OWASP Top 10:2025)*

---

#### 00. Resumen Ejecutivo y Estructura del Documento

El presente documento define el marco formal para evaluar, justificar y documentar la selección del stack tecnológico del microservicio `ms-auth`, garantizando alineación entre los atributos de calidad definidos en la norma ISO/IEC 25010 y las restricciones presupuestales y operativas del proyecto StyleRadar.

* **Servicios del Ecosistema:** 13 Microservicios + 1 API Gateway Perimetral
* **Capas Evaluadas:** 8 capas tecnológicas de extremo a extremo
* **Estándar de Empaquetado:** 100% Contenedores compatibles con OCI (Docker)
* **Latencia Meta del Sistema:** Percentil P95 inferior a 200 ms en consultas operativas

---

#### 01. Contexto y Drivers Arquitectónicos (ISO/IEC 25010)

##### 1.1 Declaración del Problema y Justificación de Microservicios

El sistema StyleRadar requiere desacoplar el procesamiento intensivo de inteligencia artificial (inferencia del probador virtual) del catálogo de alta concurrencia de lectura y de las operaciones críticas de identidad. Específicamente, `ms-auth` debe aislar las funciones de autenticación, emisión y validación de tokens criptográficos JWT, control de acceso basado en roles (RBAC) y verificación de segundo factor OTP. Si se mantuviera una arquitectura monolítica, una saturación por intentos masivos de inicio de sesión o ataques de fuerza bruta degradaría la búsqueda de prendas o el checkout de clientes. Por tanto, se adopta una arquitectura de microservicios con límites de contexto basados en Domain-Driven Design (DDD), donde `ms-auth` opera de forma autónoma con su propia base de datos.

##### 1.2 Atributos de Calidad Clave (ISO/IEC 25010)

| Atributo de Calidad | Métrica / Escenario Objetivo | Impacto en la Selección Tecnológica |
| :--- | :--- | :--- |
| **Rendimiento y Latencia** | P95 < 200 ms en consultas de catálogo; P99 < 500 ms | Exige motores de persistencia con índices B-Tree, caching distribuido L2 y frameworks con bajo overhead. |
| **Escalabilidad Horizontal** | Soporte de 0 a 1,000 req/s en eventos de ferias de moda | Microservicios stateless en contenedores OCI; escalado elástico independiente por réplicas. |
| **Consistencia de Datos** | ACID estricto en pagos, credenciales y stock; eventual en métricas | PostgreSQL para transaccionalidad relacional estricta; RabbitMQ para propagación de eventos asíncronos. |
| **Disponibilidad y Resiliencia** | SLA 99.9%; tolerancia al fallo de servicios downstream | Circuit Breakers (Resilience4j), degradación elegante si la API externa de notificación o IA no responde. |
| **Seguridad y Privacidad** | Zero Trust; JWT RS256/HS512; RBAC granular; HTTPS TLS 1.3 | Spring Cloud Gateway y Spring Security validan firmas criptográficas antes de enrutar a la red interna. |
| **Eficiencia de Costos (FinOps)** | Costo mensual < $35 USD en fase MVP / académica | Aprovechamiento de Free Tiers y serverless (Container Apps con escala a cero y databases B-series). |
| **Mantenibilidad & Equipo** | Time-to-market de 8 semanas para el core MVP | Uso de tecnologías ampliamente dominadas por el equipo (Java 21 / Spring Boot) para mitigar riesgo de entrega. |

##### 1.3 Restricciones Técnicas y Organizacionales

El sistema debe empaquetarse en imágenes Docker compatibles con estándares OCI; las pruebas unitarias e integración deben alcanzar una cobertura mínima de JaCoCo igual o superior al 85% y superar el Quality Gate estricto de SonarQube con cero vulnerabilidades y cero code smells graves. La evaluación de tecnologías en esta plantilla sigue el método ATAM (Architecture Tradeoff Analysis Method), priorizando los drivers que generan mayor impacto en los riesgos del negocio y la viabilidad técnica del sistema.

---

#### 02. Metodología de Evaluación Multicriterio (MCDM / Matriz de Pugh)

Para evitar decisiones basadas en preferencias subjetivas, se utiliza una matriz de decisión cuantitativa multicriterio (MCDM / Matriz de Pugh), asignando ponderaciones porcentuales basadas en los drivers arquitectónicos de StyleRadar.

##### 2.1 Criterios de Selección y Ponderaciones

| Criterio | Peso (%) | Definición y Justificación | Métrica Objetiva de Medición |
| :---: | :---: | :--- | :--- |
| **C1: Rendimiento** | 25 % | Capacidad de atender alto throughput con latencia mínima P95 y bajo consumo de memoria RAM y CPU. | Benchmarks oficiales, tiempo de respuesta bajo carga (JMeter/k6), consumo de memoria por contenedor. |
| **C2: Madurez & LTS** | 20 % | Estabilidad de APIs, soporte extendido (LTS), documentación oficial y tamaño del ecosistema. | Años en el mercado, frecuencia de parches CVE, comunidad activa en GitHub y respaldo de fundaciones. |
| **C3: Velocidad Dev** | 20 % | Curva de aprendizaje del equipo, disponibilidad de librerías listas para producción y velocidad de prototipado. | Horas requeridas para implementar un CRUD reactivo con seguridad JWT y persistencia relacional. |
| **C4: Operabilidad** | 15 % | Facilidad de contenedorización con Docker, compatibilidad CI/CD y observabilidad con métricas y logs. | Tamaño de la imagen Docker final, soporte nativo de métricas de salud (Actuator / Healthchecks). |
| **C5: Costo & TCO** | 20 % | Costo total de propiedad en nubes públicas, tipo de licenciamiento open-source y ausencia de vendor lock-in. | Licencia OSI (Apache 2.0 / MIT / PostgreSQL), costo de instancias administradas en Azure Calculator. |

##### 2.2 Escala de Calificación Objetiva (Rúbrica 1 a 5)

* **1 - 2 (Deficiente):** No cumple el driver. Curva prohibitiva, consumo desmedido de RAM o licenciamiento restrictivo.
* **3 (Aceptable):** Cumple parcialmente con trade-offs significativos que requieren mitigación técnica adicional.
* **4 - 5 (Sobresaliente):** Excelente desempeño, soporte LTS probado en la industria y alta productividad para el equipo.

##### 2.3 Estándar de Architectural Decision Records (ADR)

Cada decisión se documenta bajo el formato formal de ADR (Michael Nygard), sintetizando el contexto, las opciones evaluadas, la decisión final justificada y las consecuencias derivadas. La puntuación total ponderada se calcula mediante la fórmula oficial:

$$\text{Puntaje Total} = \sum_{i=1}^{5} (\text{Calificación}_{i} \times \text{Peso}_{i})$$

---

#### 03. Matrices de Decisión Técnicas Detalladas (ADRs de Capa)

##### 3.1 Lenguaje & Runtime de Ejecución Backend (ADR-001)

| Criterio (Peso) | Java 21 LTS (OpenJDK) | Node.js (TypeScript) | Go (Golang 1.22) | Python 3.12 (FastAPI) |
| :--- | :---: | :---: | :---: | :---: |
| C1: Rendimiento (25 %) | 5 | 4 | 5 | 3 |
| C2: Madurez & LTS (20 %) | 5 | 4 | 4 | 4 |
| C3: Velocidad Dev (20 %) | 4 | 4 | 3 | 5 |
| C4: Operabilidad (15 %) | 5 | 4 | 5 | 3 |
| C5: Costo & TCO (20 %) | 4 | 4 | 4 | 3 |
| **Total Ponderado** | **4.65 / 5.00 (Seleccionado)** | **3.95 / 5.00** | **4.30 / 5.00** | **3.55 / 5.00** |

* **Decisión ADR-001:** Se adopta Java 21 LTS como lenguaje estándar transversal para todos los microservicios backend de StyleRadar.
* **Por qué Java 21:** Soporte de hilos virtuales (Project Loom) para alta concurrencia de entrada/salida sin complejidad reactiva asíncrona; Records inmutables y tipado estricto ideal para modelos de dominio DDD y arquitectura hexagonal.
* **Por qué no los otros:** Node.js bloquea el Event Loop ante operaciones CPU intensivas (hashing criptográfico BCrypt); Python presenta alto consumo de RAM y debilidad en concurrencia por el Global Interpreter Lock (GIL); Go carece de un ecosistema empresarial tan integrado para seguridad y persistencia relacional como Spring.

##### 3.2 Framework Backend & Microservicios (ADR-002)

| Criterio (Peso) | Spring Boot 3.2.x | Quarkus 3.x | Micronaut 4.x | NestJS (Node/TS) |
| :--- | :---: | :---: | :---: | :---: |
| C1: Rendimiento (25 %) | 5 | 5 | 4 | 4 |
| C2: Madurez & LTS (20 %) | 5 | 4 | 3 | 4 |
| C3: Velocidad Dev (20 %) | 5 | 3 | 3 | 4 |
| C4: Operabilidad (15 %) | 5 | 5 | 4 | 4 |
| C5: Costo & TCO (20 %) | 4 | 5 | 5 | 4 |
| **Total Ponderado** | **4.80 / 5.00 (Seleccionado)** | **4.40 / 5.00** | **3.80 / 5.00** | **3.95 / 5.00** |

* **Decisión ADR-002:** Se adopta Spring Boot 3.2.x como framework base para la construcción de microservicios.
* **Por qué Spring Boot:** Integración completa y nativa con Spring Data JPA, Spring Security con soporte JWT, Spring Cloud Gateway y Spring Boot Actuator para telemetría y healthchecks. Dominio técnico previo total por parte del equipo CADche.
* **Por qué no los otros:** Quarkus y Micronaut ofrecen ventajas en arranque en frío para entornos Serverless puros mediante compilación nativa GraalVM, pero agregan fricción y complejidad en tiempo de compilación innecesaria para el ciclo de desarrollo ágil del equipo.

##### 3.3 Persistencia Relacional y Transaccional ACID (ADR-003)

| Criterio (Peso) | PostgreSQL 16 | MySQL 8.0 | MongoDB 7.0 (NoSQL) | Amazon Aurora / Cloud DB |
| :--- | :---: | :---: | :---: | :---: |
| C1: Rendimiento (25 %) | 5 | 4 | 4 | 5 |
| C2: Madurez & LTS (20 %) | 5 | 5 | 4 | 5 |
| C3: Velocidad Dev (20 %) | 5 | 4 | 4 | 4 |
| C4: Operabilidad (15 %) | 5 | 4 | 4 | 5 |
| C5: Costo & TCO (20 %) | 5 | 4 | 3 | 2 |
| **Total Ponderado** | **4.95 / 5.00 (Seleccionado)** | **4.15 / 5.00** | **3.75 / 5.00** | **4.10 / 5.00** |

* **Decisión ADR-003 (revisada en el Sprint 2):** PostgreSQL 16 es el motor relacional del stack, pero `ms-auth` **no** lo usa: su estado es efímero y vive en Redis 7, y las cuentas son de `ms-usuarios` (Database-per-Service). Ver §3.2.
* **Por qué PostgreSQL (en los servicios con datos relacionales, como ms-usuarios):** Garantiza transacciones ACID estrictas para el registro de usuarios, almacenamiento de contraseñas hasheadas, roles y auditoría de accesos; excelente control de concurrencia multiversión (MVCC) y cumplimiento de estándares SQL sin riesgo de vendor lock-in.
* **Por qué no los otros:** MySQL tiene capacidades analíticas y de indexación inferiores; MongoDB no garantiza atomicidad transaccional estricta ni integridad referencial requerida para identidades y permisos; Aurora genera vendor lock-in y costos incompatibles con el presupuesto académico.

##### 3.4 In-Memory Caching, TTL de Sesiones y Blacklist JWT (ADR-004)

| Criterio (Peso) | Redis 7.x | Memcached | Hazelcast IMDG | Ehcache (Local JVM) |
| :--- | :---: | :---: | :---: | :---: |
| C1: Rendimiento (25 %) | 5 | 5 | 4 | 5 |
| C2: Madurez & LTS (20 %) | 5 | 5 | 4 | 4 |
| C3: Velocidad Dev (20 %) | 5 | 3 | 3 | 4 |
| C4: Operabilidad (15 %) | 5 | 4 | 3 | 5 |
| C5: Costo & TCO (20 %) | 4 | 5 | 3 | 5 |
| **Total Ponderado** | **4.80 / 5.00 (Seleccionado)** | **4.30 / 5.00** | **3.40 / 5.00** | **4.40 / 5.00** |

* **Decisión ADR-004:** Se adopta Redis 7.x como capa de caché distribuido en memoria, gestión de sesiones OTP y lista negra de tokens revocados.
* **Por qué Redis:** Provee latencia sub-milisegundo, soporte nativo de expiración automática de claves por Time-To-Live (TTL de 300 segundos para códigos OTP y TTL dinámico para JWT revocados) y coordinación distribuida sin recargar PostgreSQL.
* **Por qué no los otros:** Memcached no soporta estructuras complejas ni persistencia ligera; Ehcache es local por instancia y generaría inconsistencia de revocación entre réplicas de `ms-auth`; Hazelcast introduce sobrecarga de clustering innecesaria.

##### 3.5 Mensajería Asíncrona & Event-Driven Architecture (ADR-005)

| Criterio (Peso) | RabbitMQ 3.12+ (AMQP 0-9-1) | Apache Kafka 3.6 | AWS SQS / SNS | Redis Streams |
| :--- | :---: | :---: | :---: | :---: |
| C1: Rendimiento (25 %) | 5 | 5 | 4 | 4 |
| C2: Madurez & LTS (20 %) | 5 | 5 | 5 | 3 |
| C3: Velocidad Dev (20 %) | 5 | 3 | 4 | 4 |
| C4: Operabilidad (15 %) | 5 | 2 | 5 | 4 |
| C5: Costo & TCO (20 %) | 5 | 2 | 3 | 5 |
| **Total Ponderado** | **4.95 / 5.00 (Seleccionado)** | **3.40 / 5.00** | **4.15 / 5.00** | **3.95 / 5.00** |

* **Decisión ADR-005:** Se adopta RabbitMQ 3.12+ (AMQP 0-9-1) para la arquitectura orientada a eventos; en `ms-auth`, `RabbitMQConfig` implementa `TopicExchange("styleradar.exchange")` con la cola durable `styleradar.auth.user-status-changed.q` (routing key `user.status.changed`) y DLQ.
* **Por qué RabbitMQ:** Bajo consumo de memoria (<150 MB por instancia), enrutamiento flexible por patrones de clave (`routing key`), soporte nativo del estándar AMQP 0-9-1, confirmaciones de entrega y facilidad de despliegue tanto en Docker local como en la nube mediante CloudAMQP.
* **Por qué no los otros:** Apache Kafka está sobredimensionado para el volumen de 13 microservicios y requiere clústeres pesados de RAM (mínimo 4 GB por broker); AWS SQS no permite ejecución 100% offline sin emuladores propietarios.

---

#### 04. Análisis de Costos en la Nube (TCO Azure & FinOps)

Para garantizar la viabilidad financiera del proyecto se proyecta el Costo Total de Propiedad (TCO) comparando el escenario de desarrollo/MVP contra el escenario de producción comercial escalada en Microsoft Azure.

##### 4.1 Topología Cloud y Supuestos de Dimensionamiento

Los microservicios se ejecutan como contenedores en Azure App Service, con aplicaciones separadas para QA y PROD. La base de datos corre en Azure Database for PostgreSQL Flexible Server. El almacenamiento estático se hospeda en Azure Blob Storage (Hot LRS) y el broker de mensajería en CloudAMQP.

##### 4.2 Desglose Mensual Estimado de Costos (Azure Cloud Provider)

| Capa de Servicio | SKU / Tier Fase MVP | SKU / Tier Fase Producción | Costo MVP ($) | Costo Prod ($) |
| :--- | :--- | :--- | :---: | :---: |
| **Cómputo (13 MS + GW)** | Azure Container Apps (Consumo < 180k vCPU-s) | Container Apps Dedicado (4 réplicas activas) | $0.00 (Free) | $58.00 / mes |
| **Base de Datos Relacional** | PostgreSQL Flexible Server B1ms (1 vCPU, 2GB) | PostgreSQL Flexible Server B2s (2 vCPU, 4GB, HA) | $15.50 / mes | $42.00 / mes |
| **Caché Distribuido** | Azure Cache for Redis Basic C0 (250 MB) | Azure Cache for Redis Standard C1 (1 GB Replicado) | $16.00 / mes | $38.00 / mes |
| **Broker de Mensajería** | CloudAMQP Little Lemur (Plan Gratuito Compartido) | CloudAMQP Lemur (Dedicado 1 vCPU, 1GB) | $0.00 (Free) | $19.00 / mes |
| **Storage & CDN** | Azure Blob Storage Hot (10 GB) | Azure Blob Storage (100 GB) + Azure Front Door | $1.50 / mes | $12.00 / mes |
| **IA Probador Virtual** | FASHN AI API Free Tier (50 transformaciones) | FASHN AI Pro (500 transformaciones mensuales) | $0.00 (Free) | $45.00 / mes |
| **Logs y Monitoreo** | Log Analytics / App Insights (5 GB gratis/mes) | Application Insights Pay-as-you-go | $0.00 (Free) | $12.00 / mes |
| **TOTAL MENSUAL ESTIMADO (USD)** | | | **$33.00 / mes** | **$226.00 / mes** |

##### 4.3 Directrices FinOps para Optimización de Costos

1. **Escala a cero en valle:** Configurar reglas KEDA en Azure Container Apps para apagar réplicas inactivas entre 12:00 AM y 6:00 AM en entornos de desarrollo y staging.
2. **Caché agresivo con Redis:** Almacenar tokens JWT validados y sesiones activas en Redis para evitar consultas redundantes a la base de datos relacional.
3. **Connection Pooling controlado:** HikariCP configurado a un máximo de 5 conexiones por pod en staging para evitar sobrepasar los límites de memoria RAM de la instancia PostgreSQL B1ms.

---

#### 05. Radar Tecnológico (Tech Radar)

El Technology Radar de StyleRadar categoriza las tecnologías según su nivel de adopción institucional, mitigando el riesgo de obsolescencia o dispersión técnica:

##### Cuadrante 1: Lenguajes & Frameworks
* **ADOPT:** Java 21 LTS, Spring Boot 3.2.x, React 18, Tailwind CSS, Vite.
* **TRIAL:** Spring Cloud Gateway Reactive, Spring Data Specifications.
* **ASSESS:** Virtual Threads en producción masiva, Keycloak OpenID Connect.
* **HOLD:** Monolito no modular, Node.js sin TypeScript, frameworks sin tipado.

##### Cuadrante 2: Almacenamiento & Datos
* **ADOPT:** PostgreSQL 16, PostGIS, Redis 7 (Cache L2 & Locks), Azure Blob Storage.
* **TRIAL:** Índices GIN Full-Text Search en PostgreSQL para búsqueda textil.
* **ASSESS:** pgvector para búsqueda por similitud semántica de prendas.
* **HOLD:** MongoDB para transacciones de stock, MySQL sin soporte geoespacial.

##### Cuadrante 3: Plataforma & DevOps
* **ADOPT:** Docker, Docker Compose, Azure Container Apps, GitHub Actions.
* **TRIAL:** CloudAMQP RabbitMQ gestionado, Azure Front Door CDN.
* **ASSESS:** Kubernetes (AKS) bare-metal, Istio Service Mesh, OpenTelemetry.
* **HOLD:** Instancias GPU dedicadas sin auto-escalado, despliegues manuales SSH.

##### Cuadrante 4: Herramientas & Calidad
* **ADOPT:** JUnit 5, Mockito, Testcontainers, SonarQube, JaCoCo, Flyway.
* **TRIAL:** FASHN AI VTON API, SpringDoc OpenAPI (Swagger 3 UI).
* **ASSESS:** Playwright para pruebas E2E automatizadas en web móvil.
* **HOLD:** Apache Kafka para comunicación simple entre 13 microservicios.

*Criterio de transición:* Una tecnología pasa de TRIAL a ADOPT únicamente tras validar su desempeño en un sprint productivo, haber alcanzado cobertura de pruebas igual o superior al 85% y contar con documentación de arquitectura aprobada.

---

#### 06. Matriz de Riesgos Técnicos y Estrategias de Mitigación

| ID | Riesgo Técnico Identificado | Prob. / Imp. | Impacto Potencial | Estrategia de Mitigación Diseñada |
| :---: | :--- | :---: | :--- | :--- |
| **R1** | Vendor Lock-In de Servicios Cloud | Baja / Medio | Dificultad de migrar la infraestructura si los costos de Azure aumentan imprevistamente. | Empaquetado OCI estándar (Docker), uso de APIs abiertas (PostgreSQL, AMQP 0-9-1) y repositorios agnósticos de almacenamiento. |
| **R2** | Latencia Elevada en Inferencia IA | Alta / Alto | Tiempos de espera de 8 a 15 segundos en el probador virtual degradando la UX del usuario. | Procesamiento asíncrono con colas RabbitMQ; polling o WebSockets en frontend; compresión previa de fotos a 720p. |
| **R3** | Agotamiento de Pool de Conexiones DB | Media / Crítico | 13 microservicios x N réplicas saturando el límite de conexiones de PostgreSQL Flexible Server. | Configuración restrictiva de HikariCP (`maximum-pool-size: 5`) por pod; uso de PgBouncer en capas de persistencia compartidas. |
| **R4** | Inconsistencia Distribuida en Inventario | Media / Crítico | Sobreventa de prendas de talla única en eventos de alta concurrencia de compras. | Bloqueos distribuidos en Redis (`Redlock`) con TTL de 5 minutos durante checkout y patrón Transaccional Outbox. |
| **R5** | Deuda Técnica por Curva de Aprendizaje | Baja / Medio | Errores de configuración o lentitud de entrega por adopción de stacks desconocidos. | Unificación del stack core en Java 21 / Spring Boot dominado por el equipo; Quality Gate obligatorio en CI/CD con SonarQube. |

##### Riesgo Crítico Específico de `ms-auth`: Ataques de Fuerza Bruta y Saturación OTP
* **Riesgo:** Intentos masivos automatizados de adivinación de contraseñas o solicitudes continuas de códigos OTP para agotar saldo del proveedor SMTP.
* **Mitigación:** Filtro de limitación de tasa por IP en Spring Cloud Gateway y Redis; bloqueo temporal automático de la cuenta tras 3 intentos fallidos de OTP o 5 intentos fallidos de login; expiración estricta por TTL de 300 segundos administrada directamente en memoria por Redis.

---

#### 07. Matriz de Asignación Tecnológica por Microservicio (13 Servicios + GW)

Consolidación técnica oficial del ecosistema StyleRadar:

| Servicio | Puerto | Paquete · Equipo |
| :--- | :---: | :--- |
| `api-gateway` | `8080` | Paquete 5 · Estación Portal 8080 (por convención) |
| **`ms-auth`** | **`8100`** | **Paquete 1: Identidad · Estación CADche** |
| `ms-usuarios` | `8120` | Paquete 1: Identidad · Estación CADche |
| `ms-tiendas` | `8140` | Paquete 1: Identidad · Estación CADche |
| `ms-inventario` | `8200` | Paquete 2: Catálogo · Estación RESTrepo |
| `ms-item-catalogo` | `8220` | Paquete 2: Catálogo · Estación RESTrepo |
| `ms-geolocalizacion` | `8240` | Paquete 2: Catálogo · Estación RESTrepo |
| `ms-playlist` | `8260` | Paquete 2: Catálogo · Estación RESTrepo |
| `ms-notificaciones` | `8300` | Paquete 3: Notificaciones, Chat y Broker · Guatoque Jiraguas |
| `ms-chat` | `8320` | Paquete 3 · Guatoque Jiraguas |
| `event-broker` (RabbitMQ) | `5672` | Paquete 3 · Guatoque Jiraguas (por convención) |
| `ms-pagos-suscripciones` | `8400` | Paquete 4: Pagos y Estadísticas · NQS |
| `ms-estadisticas` | `8420` | Paquete 4 · NQS |
| `ms-probador-virtual` | `8500` | Paquete 5: Probador, Donaciones y Gateway · Portal 8080 |
| `ms-segunda-vida-donaciones` | `8520` | Paquete 5 · Portal 8080 |

Cada equipo tiene un bloque exclusivo por centena con saltos de 20 entre microservicios, para evitar conflictos de puertos en local y en Docker.

*Aislamiento por base de datos (Database-per-Service):* Cada microservicio posee su propio esquema o base de datos aislada en PostgreSQL para garantizar bajo acoplamiento e independencia total de despliegue.

##### Justificación de las Cuatro Preguntas Clave para `ms-auth`:

1. **¿Requiere Base de Datos o no?**
   **Sí requiere almacenamiento operativo de estado (actualmente Redis 7; PostgreSQL 16 parametrizado/diferido).** Aunque el protocolo HTTP es *stateless* mediante JWT Bearer, `ms-auth` requiere persistir en memoria compartida (`Redis 7`) los hashes SHA-256 OTP (`300 s`), contadores de intentos/reenvíos, estados anti-CSRF Google (`oauth2:state`), ventanas de *rate limiting*, tokens revocados (`blacklist:*`) y sellos de revocación por cuenta (`usuario:revocado:*`). Los perfiles maestros y contraseñas residen hoy en `ms-usuarios`, quedando PostgreSQL 16 parametrizado para futuras entidades locales/auditoría.

2. **Tipo de Base de Datos y Motor Seleccionado:**
   **Arquitectura políglota: Clave-Valor activo en `Redis 7` + Relacional `PostgreSQL 16` parametrizado.** `Redis 7` provee expiración automática por TTL (`300 s` para OTP y `oauth2:state`, `60 s` para rate limiting, TTL restante para JWT blacklist) y validación sub-milisegundo en filtros de seguridad (`JwtAuthenticationFilter`, `RateLimitingFilter`). `PostgreSQL 16` + `Flyway 10.20.1` (`FLYWAY_ENABLED=false`, excluido en `dev`/`test`) permanece listo para cuando `ms-auth` active persistencia SQL propia.

3. **Frameworks, Lenguaje y Herramientas:**
   **Java 21 LTS, Spring Boot 3.2.12, Spring Security 6, JJWT `0.12.6` (`HS256`), Spring Security OAuth2 JOSE (`NimbusJwtDecoder RS256/JWKS`), SpringDoc OpenAPI `2.3.0`, JaCoCo `0.8.12`, SpotBugs `4.8.6.6` y CycloneDX.** `Java 21` aporta `Records` inmutables; `JJWT` firma tokens internos con clave Base64 >= 256 bits, `NimbusJwtDecoder` valida tokens OIDC de Google contra sus claves JWKS públicas y la arquitectura hexagonal minimalista desacopla el puerto `GoogleOAuthPort` de los adaptadores externos (`GoogleOAuthAdapter`, `UsuarioClient`).

4. **Broker de Mensajería:**
   **RabbitMQ 3.12+ con protocolo AMQP 0-9-1 (`TopicExchange("styleradar.exchange")`).** `ms-auth` consume la cola durable `styleradar.auth.user-status-changed.q` (`routing key`: `user.status.changed`) mediante `UsuarioEstadoCambiadoConsumer`, registrando `usuario:revocado:{id}` en Redis cuando `ms-usuarios` bloquea o inactiva una cuenta (y propagando `DataAccessException` para no perder eventos). Además publica `user.session.changed` (`RabbitMQSesionEventPublisherAdapter`) para que ms-estadísticas lleve el conteo de sesiones activas. El envío OTP se realiza síncronamente vía SMTP (`EmailServiceImpl`).

---

#### 08. Conclusiones Arquitectónicas y Validación Formal

##### 8.1 Resumen de Decisiones y Factores de Éxito

La adopción de Java 21 LTS y Spring Boot 3.2.x garantiza un ecosistema robusto, tipado y altamente mantenible. La persistencia híbrida con PostgreSQL 16 y Redis 7 resuelve con máxima eficiencia la integridad ACID de las credenciales de identidad y la latencia sub-milisegundo en la validación de sesiones. El uso de RabbitMQ 3.12+ desacopla los microservicios sin la sobrecarga operativa ni los costos de memoria de Kafka, asegurando una solución viable técnica y financieramente para StyleRadar.

##### 8.2 Roadmap de Evolución Tecnológica

| Fase / Sprint | Objetivo Tecnológico Clave | Criterio de Aceptación |
| :--- | :--- | :--- |
| **Sprint 1 (Semanas 1-2)** | Core arquitectónico: Gateway, Autenticación JWT y DB schemas. | Tokens validados en Gateway; Flyway migraciones ejecutadas. |
| **Sprint 2 (Semanas 3-4)** | Catálogo, geolocalización PostGIS y stock con Redis Redlock. | Búsquedas geoespaciales < 200 ms; prevención de sobreventa. |
| **Sprint 3 (Semanas 5-6)** | Integración de pagos (Wompi) y probador virtual (FASHN AI). | Webhooks procesados con idempotencia; inferencia asíncrona. |
| **Sprint 4 (Semanas 7-8)** | Pruebas E2E, Quality Gate SonarQube ≥ 85 % y despliegue cloud. | 0 vulnerabilidades; pipeline CI/CD en Azure automatizado. |

##### 8.3 Acta de Validación y Firmas del Equipo de Arquitectura

| Rol en el Proyecto | Nombre del Ingeniero | Decisión / Estado | Firma / Aprobación |
| :--- | :--- | :---: | :---: |
| **Líder de Proyecto / Product Owner** | Juan Nicolás Álvarez | APROBADO | `J. Nicolás Álvarez` |
| **Arquitecto de Software / Tech Lead** | Camilo Ortiz | APROBADO | `Camilo Ortiz` |
| **Scrum Master / Backend Developer** | Daniel Valero | APROBADO | `Daniel Valero` |
| **Fullstack / Integración Pagos** | Juan Diego Valderrama | APROBADO | `J. D. Valderrama` |
| **QA Lead & DevOps Engineer** | Paula Alejandra Díaz | APROBADO | `Paula A. Díaz` |

*Aprobación para el Desarrollo:* El presente documento constituye la línea base técnica (Technical Baseline) autorizada por el equipo CADche para el desarrollo de los microservicios de StyleRadar.

---

[⬅ Volver al README Principal](../../README.md)
