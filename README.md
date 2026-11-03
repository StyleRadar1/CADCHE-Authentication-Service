<!-- ==============================================================================
  STYLE RADAR 
  Asignatura: DOSW · 2026
  Estándar obligatorio unificado para los 13 microservicios del proyecto StyleRadar.
============================================================================== -->

<div align="center">

  <a href="https://github.com/StyleRadar1">
    <img src="https://raw.githubusercontent.com/JUANNICOLASA/Frontend-Style-Radar/main/styleradar-frontend-v3/assets/logo/styleradar-logo.svg" width="340" alt="StyleRadar Logo Oficial" />
  </a>

  <h1>STYLE<font color="#5600EF">RADAR</font> &nbsp;·&nbsp; <code>ms-auth</code></h1>

  <p align="center">
    <img src="https://img.shields.io/badge/BOUNDED_CONTEXT-Autenticaci%C3%B3n_e_Identidad_(IAM)-12063A?style=for-the-badge" alt="Context" />
    <img src="https://img.shields.io/badge/PUERTO_OFICIAL-8081-5600EF?style=for-the-badge" alt="Port" />
    <img src="https://img.shields.io/badge/ARQUITECTURA-HEXAGONAL-16A34A?style=for-the-badge" alt="Hexagonal" />
  </p>

  <p align="center">
    <strong><font color="#5600EF">Descubre tu estilo. En tu ciudad.</font></strong><br />
    <em>Plataforma de moda local con inventario multi-sede geolocalizado en tiempo real y probador virtual para Bogotá.</em>
  </p>

  <p align="center">
    <a href="https://styleradar-backend-prod.azurewebsites.net/swagger-ui/index.html">
      <img src="https://img.shields.io/badge/PROD-Active-16A34A?style=for-the-badge&logo=azure-app-services&logoColor=white" alt="PROD Deployment" />
    </a>
    <a href="https://styleradar-backend-qa.azurewebsites.net/swagger-ui/index.html">
      <img src="https://img.shields.io/badge/QA-Active-5600EF?style=for-the-badge&logo=azure-app-services&logoColor=white" alt="QA Deployment" />
    </a>
    <a href="https://sonarcloud.io/organizations/styleradar/projects">
      <img src="https://img.shields.io/badge/SonarQube-Quality_Gate_Passed-16A34A?style=for-the-badge&logo=sonarqube&logoColor=white" alt="Quality Gate" />
    </a>
    <a href="target/site/jacoco/index.html">
      <img src="https://img.shields.io/badge/JaCoCo_Coverage-%E2%89%A5_85%25-16A34A?style=for-the-badge&logo=codecov&logoColor=white" alt="Coverage" />
    </a>
    <a href="#6-tecnologías-y-dependencias">
      <img src="https://img.shields.io/badge/Java-21_LTS-12063A?style=for-the-badge&logo=openjdk&logoColor=C4B5FD" alt="Java 21" />
    </a>
    <a href="#6-tecnologías-y-dependencias">
      <img src="https://img.shields.io/badge/Spring_Boot-3.2.12-12063A?style=for-the-badge&logo=spring&logoColor=C4B5FD" alt="Spring Boot 3.2.12" />
    </a>
    <a href="#7-cómo-ejecutar-local-y-con-docker">
      <img src="https://img.shields.io/badge/Docker-OCI_Container-12063A?style=for-the-badge&logo=docker&logoColor=C4B5FD" alt="Docker OCI" />
    </a>
  </p>

</div>


---

## <img src="https://img.shields.io/badge/ÍNDICE-5600EF?style=for-the-badge" height="20" align="absmiddle" /> ÍNDICE DE CONTENIDOS

1. [Nombre del servicio y su bounded context](#1-nombre-del-servicio-y-su-bounded-context)
2. [Equipo y responsables](#2-equipo-y-responsables)
3. [Arquitectura hexagonal con su diagrama](#3-arquitectura-hexagonal-con-su-diagrama)
4. [Endpoints y enlace a Swagger](#4-endpoints-y-enlace-a-swagger)
5. [Eventos que publica y consume](#5-eventos-que-publica-y-consume)
6. [Tecnologías y dependencias](#6-tecnologías-y-dependencias)
7. [Cómo ejecutar local y con Docker](#7-cómo-ejecutar-local-y-con-docker)
8. [Variables de entorno](#8-variables-de-entorno)
9. [Pruebas y cobertura](#9-pruebas-y-cobertura)
10. [CI/CD con el badge del pipeline](#10-cicd-con-el-badge-del-pipeline)
11. [Ramas y cómo contribuir](#11-ramas-y-cómo-contribuir)
12. [Enlace a la épica en Jira](#12-enlace-a-la-épica-en-jira)

---

<a id="1-nombre-del-servicio-y-su-bounded-context"></a>

## <img src="https://img.shields.io/badge/01-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 1. NOMBRE DEL SERVICIO Y SU BOUNDED CONTEXT

* **Identificador de Servicio:** `ms-auth` (`CADCHE-Authentication-Service`)
* **Paquete Arquitectónico:** `Paquete 1: Identidad`
* **Puerto de Escucha Oficial:** `8081` (definido en la matriz de puertos sin colisión)
* **Base de Datos Asignada:** `PostgreSQL 16` (`styleradar_auth`) para credenciales y sesiones + `Redis 7.x` para códigos OTP efímeros (TTL 5 min) y lista negra de JWT revocados, aislada bajo el patrón *Database-per-Service*.
* **Bounded Context:** `Autenticación, Identidad y Control de Acceso (IAM)`

### Responsabilidades del Dominio

Este microservicio encapsula exclusivamente la lógica de negocio correspondiente a `Autenticación, Identidad y Control de Acceso (IAM)`.

| Qué HACE este Microservicio (Alcance) | Qué NO HACE este Microservicio (Límites) |
| :--- | :--- |
| • Autentica de forma unificada a los cuatro roles (`COMPRADOR`, `ADMIN_ALMACEN`, `REPRESENTANTE_FUNDACION`, `ADMIN_STYLERADAR`) y emite tokens JWT (`accessToken` y `refreshToken`) firmados (`SR-RF-02`). | • NO gestiona preferencias de estilo, tallas habituales ni búsquedas guardadas del comprador (delegado a `ms-usuarios` en `:8082`). |
| • Genera, valida y reenvía códigos OTP de 6 dígitos con vigencia estricta de 5 minutos (`300s`) y bloqueo tras 3 intentos fallidos (`SR-RF-92`). | • NO administra almacenes aliados, verificación de RUT ni fundaciones aliadas (delegado a `ms-tiendas` en `:8083`). |
| • Registra credenciales de usuario comprador con cifrado `BCrypt` (costo ≥ 10), valida la aceptación expresa de Habeas Data (`SR-RF-01`, `SR-RNF-02`) y revoca sesiones en logout (`SR-RNF-01`, Deuda Técnica #2). | • NO envía correos electrónicos ni WebSockets de forma directa (publica eventos hacia `ms-notificaciones` en `:8092`). |
| • Garantiza persistencia ACID e idempotencia en su base de datos (`styleradar_auth`). | • NO accede directamente a bases de datos de otros microservicios. |

### Arquetipos de Moda y Personajes de StyleRadar

<div align="center">

| Antonia (Sastre) | Mateo (Calle) | Aura (Futuro) | Nora (Rebelde) | Tomás (Esencial) |
| :---: | :---: | :---: | :---: | :---: |
| <img src="https://raw.githubusercontent.com/JUANNICOLASA/Frontend-Style-Radar/main/styleradar-frontend-v3/assets/mascota/antonia.jpg" width="110" style="border-radius:8px;" alt="Antonia" /><br>**Sastre**<br><sub>Tiendas & Descubrimiento</sub> | <img src="https://raw.githubusercontent.com/JUANNICOLASA/Frontend-Style-Radar/main/styleradar-frontend-v3/assets/mascota/mateo.jpg" width="110" style="border-radius:8px;" alt="Mateo" /><br>**Calle**<br><sub>Playlists & Cultura Urbana</sub> | <img src="https://raw.githubusercontent.com/JUANNICOLASA/Frontend-Style-Radar/main/styleradar-frontend-v3/assets/mascota/aura.jpg" width="110" style="border-radius:8px;" alt="Aura" /><br>**Futuro**<br><sub>Probador Virtual AI</sub> | <img src="https://raw.githubusercontent.com/JUANNICOLASA/Frontend-Style-Radar/main/styleradar-frontend-v3/assets/mascota/nora.jpg" width="110" style="border-radius:8px;" alt="Nora" /><br>**Rebelde**<br><sub>Segunda Vida & Vintage</sub> | <img src="https://raw.githubusercontent.com/JUANNICOLASA/Frontend-Style-Radar/main/styleradar-frontend-v3/assets/mascota/tomas.jpg" width="110" style="border-radius:8px;" alt="Tomás" /><br>**Esencial**<br><sub>Fundaciones & Donaciones</sub> |

</div>

---

<a id="2-equipo-y-responsables"></a>

## <img src="https://img.shields.io/badge/02-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 2. EQUIPO Y RESPONSABLES

Desarrollado y mantenido por el **Squad `CADche` (`CADCHE`)** para la asignatura DOSW 2026.

<div align="center">

| Rol | Nombre Completo | Usuario GitHub |
| :---: | :---: | :---: |
| **Tech Lead / Arquitecto & Backend Developer** | `Julian Camilo Giral` | `[@jcobos548](https://github.com/jcobos548)` |
| **Product Owner / Scrum Master & Backend Developer** | `Juan Diego Gaitan` | `[@mamagege](https://github.com/mamagege)` |

</div>

---

<a id="3-arquitectura-hexagonal-con-su-diagrama"></a>

## <img src="https://img.shields.io/badge/03-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 3. ARQUITECTURA HEXAGONAL CON SU DIAGRAMA

El microservicio implementa **Arquitectura Hexagonal (Puertos y Adaptadores)** combinada con **Domain-Driven Design (DDD)**. El núcleo de negocio permanece 100% puro y aislado de frameworks, bases de datos o brokers de mensajería.

### Diagrama Interactivo de Capas (Mermaid)

```mermaid
flowchart TD
    subgraph INFRA_IN["Infraestructura de Entrada (Driving Adapters)"]
        REST["AuthController / AuthApi<br>(Spring Web / OpenAPI 3)"]
        AMQP_IN["RabbitMQ Listener<br>(Spring AMQP Consumer)"]
    end

    subgraph APP["Capa de Aplicación (Application Layer)"]
        IN_PORT["Input Ports<br>(Interfaces de Casos de Uso de Auth y OTP)"]
        USE_CASES["Use Cases & Services Impl<br>(Login, Registro, Refresh, Logout, OTP)"]
        OUT_PORT["Output Ports<br>(Interfaces de Persistencia, Seguridad y Mensajería)"]
    end

    subgraph DOMAIN["Núcleo de Dominio (Domain Core - Java Puro)"]
        ENTITIES["Entidades y Modelos de Dominio<br>(Credencial, Sesion, CodigoOtp, Rol)"]
        VO["Value Objects<br>(Email, PasswordHash, TokenClaims)"]
        EVENTS["Domain Events<br>(UsuarioRegistrado, OtpGenerado, SesionRevocada)"]
        EXCEPTIONS["Domain Exceptions<br>(CredencialesInvalidas, OtpExpirado, CuentaBloqueada)"]
    end

    subgraph INFRA_OUT["Infraestructura de Salida (Driven Adapters)"]
        JPA_REPO["JPA Adapter<br>(Spring Data / PostgreSQL 16)"]
        JWT_ADAPTER["JWT & BCrypt Adapter<br>(JJWT 0.12.6 / Spring Security)"]
        RABBIT_PUB["RabbitMQ Publisher<br>(Spring AMQP Producer)"]
        REDIS_ADAPTER["Redis Adapter<br>(OTP TTL 5m & JWT Blacklist)"]
    end

    REST --> IN_PORT
    AMQP_IN --> IN_PORT
    IN_PORT --> USE_CASES
    USE_CASES --> DOMAIN
    USE_CASES --> OUT_PORT
    OUT_PORT --> JPA_REPO
    OUT_PORT --> JWT_ADAPTER
    OUT_PORT --> RABBIT_PUB
    OUT_PORT --> REDIS_ADAPTER

    classDef domain fill:#EDE7FF,stroke:#5600EF,stroke-width:2px,color:#0C0C12;
    classDef app fill:#F8FAFC,stroke:#C4B5FD,stroke-width:2px,color:#0C0C12;
    classDef infra fill:#12063A,stroke:#5600EF,stroke-width:1.5px,color:#FFFFFF;

    class DOMAIN,ENTITIES,VO,EVENTS,EXCEPTIONS domain;
    class APP,IN_PORT,USE_CASES,OUT_PORT app;
    class INFRA_IN,INFRA_OUT,REST,AMQP_IN,JPA_REPO,JWT_ADAPTER,RABBIT_PUB,REDIS_ADAPTER infra;
```

<div align="center">
  <table width="100%" style="margin: 18px 0; border: 2px dashed #C4B5FD; border-radius: 8px; background-color: #FBFBFE;">
    <tr>
      <td align="center" style="padding: 20px;">
        <p>
          <img src="https://img.shields.io/badge/ESPACIO_DE_DIAGRAMA-DRAW.IO_/_PLANTUML-12063A?style=for-the-badge" alt="Diagramas de Arquitectura" /><br />
          <strong><font color="#5600EF" size="3">DIAGRAMAS DE ARQUITECTURA HEXAGONAL Y UML DEL SERVICIO</font></strong>
        </p>
        <p>
          <sub>Los diagramas de clases, base de datos, componentes específico y secuencia se versionan en <code>docs/uml/</code>. Archivos fuente Draw.io: <a href="docs/uml/DiagramaClases.drawio"><code>DiagramaClases.drawio</code></a> · <a href="docs/uml/DiagramaBaseDatos.drawio"><code>DiagramaBaseDatos.drawio</code></a> · <a href="docs/uml/DiagramaComponentesEspecifico.drawio"><code>DiagramaComponentesEspecifico.drawio</code></a>.</sub>
        </p>
      </td>
    </tr>
  </table>
</div>

#### 7.1 Diagrama de Clases de Dominio (`ms-auth`)

<div align="center">
  <img src="docs/uml/DiagramaClases.png" width="95%" alt="Diagrama de Clases ms-auth" /><br />
  <sub>Figura 7.1: Diagrama de clases del núcleo de dominio puro para ms-auth (Draw.io editable en <a href="docs/uml/DiagramaClases.drawio"><code>docs/uml/DiagramaClases.drawio</code></a>).</sub>
</div>

##### Justificación de Diseño y Principios Arquitecturales
El diagrama de clases de `ms-auth` modela el núcleo de autenticación y seguridad siguiendo los lineamientos de Domain-Driven Design y el principio de Inversión de Dependencias:
- **Pureza de Dominio (0% Frameworks)**: Las clases de dominio no contienen anotaciones de persistencia como `@Entity` ni estereotipos de Spring como `@Component` o `@Service`. Esto garantiza que la lógica de seguridad y validación sea agnóstica de la infraestructura y pueda ser probada mediante pruebas unitarias puras sin levantar el contexto de Spring.
- **Inmutabilidad y Validación de Value Objects**: Los conceptos como `Email`, `PasswordHash`, `TokenJwt` y `SesionOtp` se modelan como Value Objects inmutables que encapsulan sus invariantes en el momento de instanciación, impidiendo la existencia de estados inconsistentes en el sistema.
- **Segregación de Responsabilidades y Manejo de Políticas**: La lógica de verificación de contraseñas robustas y control de intentos fallidos se delega a validadores de dominio dedicados (`PasswordRequestValidator`, `OtpAttemptValidator`), desacoplando la entidad raíz de algoritmos volátiles de seguridad.

##### Explicación Detallada de los Elementos
1. **Agregado Raíz `Credencial`**:
   - `id`: Identificador global único de tipo UUID.
   - `email`: Value Object que garantiza formato RFC 5322 válido y normalización a minúsculas.
   - `passwordHash`: Hash criptográfico seguro generado mediante BCrypt o Argon2id.
   - `rol`: Enumeración de dominio (`COMPRADOR`, `VENDEDOR`, `ADMINISTRADOR`).
   - `estado`: Estado de la cuenta (`ACTIVO`, `BLOQUEADO`, `PENDIENTE_VERIFICACION`).
   - `intentosFallidos`: Contador entero de intentos consecutivos fallidos de autenticación.
   - `bloqueadoHasta`: Marca temporal que rige el bloqueo temporal preventivo tras superar 5 intentos fallidos.
   - Métodos de negocio: `verificarPassword()`, `registrarIntentoFallido()`, `reiniciarIntentos()`, `bloquearTemporalmente()`, `cambiarPassword()`.
2. **Entidad `ConsentimientoHabeasData`**:
   - Modela el cumplimiento legal de la Ley 1581 de 2012 y normativas de privacidad.
   - Atributos: `versionPolitica`, `fechaAceptacion`, `ipOrigen`, `canalRegistro`, `estado`.
3. **Value Objects**:
   - `Email`: Encapsula la dirección de correo y reglas de formato.
   - `PasswordHash`: Garantiza que nunca transite texto plano en la capa de dominio.
   - `SesionOtp`: Representa el desafío de autenticación en dos pasos con código hash, expiración y límite de 3 intentos.
4. **Validadores de Dominio**:
   - `CredencialValidator`: Verifica integridad de la entidad y coherencia de estados.
   - `PasswordRequestValidator`: Aplica reglas OWASP sobre longitud mínima, caracteres especiales, números y entropía.
   - `OtpAttemptValidator`: Valida vigencia temporal y número de reintentos antes del bloqueo.
5. **Puertos de Entrada y Salida**:
   - Puertos de Entrada: `LoginUseCase`, `RegisterUserUseCase`, `VerifyOtpUseCase`, `RefreshTokenUseCase`.
   - Puertos de Salida: `CredencialRepositoryPort`, `TokenProviderPort`, `RedisBlacklistPort`, `EventPublisherPort`.

---

#### 7.2 Diagrama de Base de Datos Relacional y No Relacional (`ms-auth`)

<div align="center">
  <img src="docs/uml/DiagramaBaseDatos.png" width="95%" alt="Diagrama de Base de Datos ms-auth" /><br />
  <sub>Figura 7.2: Esquema de base de datos relacional PostgreSQL 15 y motor NoSQL Redis para ms-auth (Draw.io editable en <a href="docs/uml/DiagramaBaseDatos.drawio"><code>docs/uml/DiagramaBaseDatos.drawio</code></a>).</sub>
</div>

##### Justificación del Modelo Híbrido (Database-per-Service & Polyglot Persistence)
El microservicio `ms-auth` implementa una estrategia de persistencia políglota para resolver dos requisitos con naturalezas operativas completamente opuestas:
- **Base de Datos Relacional (PostgreSQL 15)**: La identidad y las credenciales de los usuarios constituyen el activo más crítico de StyleRadar. Se requiere consistencia ACID estricta, integridad referencial y aislamiento transaccional para garantizar que no existan duplicados de cuentas, que los registros de consentimiento legal de Habeas Data no puedan quedar huérfanos y que el historial de auditoría de logins sea persistente e inmutable.
- **Motor NoSQL / En Memoria (Redis 7)**: La verificación de tokens revocados (Blacklist de JWT al cerrar sesión) y la gestión de códigos OTP de autenticación en dos pasos requieren latencia sub-milisegundo. Consultar una base de datos relacional en cada petición HTTP authenticated para comprobar si el JWT fue revocado degradaría severamente el throughput. Redis permite lecturas ultra-rápidas mediante almacenamiento clave-valor en memoria RAM y dispone de expiración nativa mediante TTL (Time-To-Live), eliminando la necesidad de procesos batch para purgar tokens vencidos u OTPs obsoletos.

##### Explicación Detallada del Esquema de Datos
1. **Esquema Relacional PostgreSQL (`styleradar_auth`)**:
   - **Tabla `credenciales`**:
     - `id (UUID, PK)`: Identificador primario no secuencial.
     - `email (VARCHAR(150), UNIQUE, NOT NULL)`: Correo electrónico con índice único para búsquedas rápidas en el login.
     - `password_hash (VARCHAR(255), NOT NULL)`: Hash seguro de la contraseña.
     - `rol (VARCHAR(30), NOT NULL)`: Rol de autorización RBAC.
     - `estado (VARCHAR(30), NOT NULL)`: Estado operativo de la cuenta.
     - `intentos_fallidos (INT, DEFAULT 0)`: Control de fuerza bruta.
     - `bloqueado_hasta (TIMESTAMP WITH TIME ZONE, NULL)`: Fin del bloqueo temporal.
     - `created_at, updated_at (TIMESTAMP WITH TIME ZONE, NOT NULL)`: Auditoría temporal.
   - **Tabla `consentimientos_habeas_data`**:
     - `id (UUID, PK)`: Identificador primario.
     - `credencial_id (UUID, FK, NOT NULL)`: Referencia con clave foránea a `credenciales(id)` con `ON DELETE CASCADE`.
     - `version_politica (VARCHAR(20), NOT NULL)`: Versión de términos aceptada.
     - `ip_registro (VARCHAR(45), NOT NULL)`: Registro legal de IP de aceptación.
     - `fecha_consentimiento (TIMESTAMP WITH TIME ZONE, NOT NULL)`: Marca temporal de aceptación.
   - **Tabla `historial_autenticacion`**:
     - `id (UUID, PK)`: Identificador de evento.
     - `credencial_id (UUID, FK, NOT NULL)`: Referencia a la credencial.
     - `tipo_evento (VARCHAR(50), NOT NULL)`: `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `OTP_ENVIADO`, `PASSWORD_RESET`.
     - `direccion_ip (VARCHAR(45), NOT NULL)`: IP de la solicitud.
     - `user_agent (VARCHAR(255), NOT NULL)`: Navegador o dispositivo origen.
     - `fecha_evento (TIMESTAMP WITH TIME ZONE, NOT NULL)`: Momento del evento.
2. **Estructura NoSQL Clave-Valor en Redis**:
   - **Namespace `blacklist:{jti}`**:
     - Estructura: String o Set con clave `blacklist:<jti>` (identificador único del JWT) y valor `REVOKED`.
     - TTL: Configurado exactamente con los segundos restantes para la expiración natural del token JWT. Una vez transcurrido este tiempo, Redis desaloca la memoria automáticamente.
   - **Namespace `otp:{email}`**:
     - Estructura: Hash con campos `code_hash` (código de 6 dígitos hasheado), `attempts` (contador de intentos realizados, máximo 3) y `created_at`.
     - TTL: 300 segundos (5 minutos). Si el usuario no valida el código dentro de esa ventana temporal, la clave desaparece sin intervención manual.

---

#### 7.3 Diagrama de Componentes Específico en Arquitectura Hexagonal (`ms-auth`)

<div align="center">
  <img src="docs/uml/DiagramaComponentesEspecifico.png" width="95%" alt="Diagrama de Componentes Específico ms-auth" /><br />
  <sub>Figura 7.3: Diagrama de componentes específico en arquitectura hexagonal para ms-auth (Draw.io editable en <a href="docs/uml/DiagramaComponentesEspecifico.drawio"><code>docs/uml/DiagramaComponentesEspecifico.drawio</code></a>).</sub>
</div>

##### Justificación del Enfoque Hexagonal (Puertos y Adaptadores)
El microservicio `ms-auth` aplica estrictamente el estándar hexagonal de StyleRadar y el checklist de 10 puntos de calificación:
- **Desacoplamiento Tecnológico**: Los adaptadores de entrada (Controladores REST y consumidores AMQP) no contienen lógica de negocio; su única responsabilidad es deserializar la petición, validar el formato de entrada mediante anotaciones Jakarta y delegar la ejecución a través de la interfaz del puerto de entrada (`domain/port/in/`).
- **Inversión de Dependencias (DIP)**: La capa de aplicación y el núcleo de dominio no conocen las implementaciones de base de datos ni los clientes de mensajería. Dependen exclusivamente de interfaces de puertos de salida (`domain/port/out/`). Las implementaciones concretas residen en `infrastructure/adapter/out/` e inyectan los repositorios de Spring Data y clientes Redis.
- **Configuración IoC Manual**: La capa de aplicación no utiliza `@Service` ni `@Component`. El cableado de dependencias se realiza de manera explícita en `BeanConfig.java` dentro de la capa de infraestructura, manteniendo la aplicación desacoplada del contenedor de Spring.

##### Explicación Detallada de los Flujos de Componentes
1. **Flujo 1: Login, Emisión/Refresh de JWT y Revocación en Blacklist (SR-RF-02, SR-RNF-01)**:
   - **Entrada (Driving)**: `AuthController` (`infrastructure/adapter/in/rest/`) expone los endpoints `POST /login`, `POST /refresh` y `POST /logout`. Implementa la interfaz contractual `AuthApi` de documentación OpenAPI.
   - **Puerto In**: Interfaz `LoginUseCase` (`domain/port/in/`).
   - **Aplicación**: `LoginUseCaseImpl` (`application/usecase/`) orquesta la operación y se apoya en los mappers `LoginDtoInMapper` y `AuthDtoOutMapper` (`application/mapper/in/` y `out/`). Invoca la interfaz `AuthService` implementada en `AuthServiceImpl` (`application/service/impl/`).
   - **Dominio Puro**: `AuthServiceImpl` opera sobre el modelo `Credencial`, el Value Object `Email` y el validador `CredencialValidator` (100% puro, 0% Spring).
   - **Puerto Out**: Invoca `CredencialRepositoryPort` y `TokenProviderPort` (`domain/port/out/`).
   - **Adaptadores Driven**: `CredencialPersistenceMapper` traduce el modelo de dominio a `CredencialJpaEntity`. `CredencialRepositoryAdapter` consulta a `SpringDataCredencialRepo` hacia PostgreSQL 15, mientras que `JwtTokenAdapter` y `RedisBlacklistAdapter` gestionan la firma de tokens y el almacenamiento en Redis.
2. **Flujo 2: Registro de Comprador, Políticas OWASP y Consentimiento Habeas Data (SR-RF-01, SR-RNF-02)**:
   - **Entrada**: `RegistroAuthController` recibe la solicitud `POST /api/v1/auth/register`.
   - **Puerto In y Aplicación**: Se conecta a `RegisterUserUseCase` e invoca `RegisterUserUseCaseImpl` con apoyo de `RegisterUserDtoInMapper` y `RegisterUserDtoOutMapper`.
   - **Servicio y Dominio**: `RegistroAuthServiceImpl` invoca a `HabeasDataServiceImpl`. Se validan las políticas de contraseña mediante `PasswordRequestValidator` y se instancia el evento de dominio `UserRegisteredEvent`.
   - **Salida**: Se persiste el consentimiento mediante `ConsentimientoPersistenceMapper` y `UserRepositoryAdapter` en PostgreSQL, y se emite el evento a través de `EventPublisherPort` hacia `RabbitMqEventPublisherAdapter` con destino al Topic Exchange `auth.events`.
3. **Flujo 3: Verificación en Dos Pasos 2FA / OTP y Auditoría de Intentos (SR-RF-92)**:
   - **Entrada**: `OtpVerificationController` atiende `POST /verify-otp` y `POST /resend-otp`.
   - **Puerto In y Aplicación**: Conexión a `VerifyOtpUseCase` y ejecución en `VerifyOtpUseCaseImpl`, asistido por `VerifyOtpDtoInMapper` y `OtpChallengedDtoOutMapper`.
   - **Servicio y Dominio**: `OtpVerificationServiceImpl` utiliza el modelo `OtpSession`, el Value Object `OtpSessionOtp` y la regla de negocio pura `OtpAttemptValidator` (control de máximo 3 intentos y ventana de 5 minutos).
   - **Salida**: `OtpSessionPersistenceMapper` y `AuditoriaPersistenceMapper` se conectan con `RedisOtpSessionAdapter` (lectura/escritura veloz con TTL en Redis) y `AuditoriaRepositoryAdapter` (registro inmutable del evento en PostgreSQL).
4. **Banda Transversal de Infraestructura**:
   - `BeanConfig.java` (`infrastructure/config/`): Instancia manualmente los beans de casos de uso y servicios de aplicación sin requerir anotaciones en el dominio ni en la aplicación.
   - `SecurityConfig.java` & `JwtAuthFilter`: Configura la seguridad stateless, la validación de firmas JWT contra Redis y la prevención de accesos no autorizados.
   - `GlobalExceptionHandler.java` (`infrastructure/exception/`): Captura excepciones de dominio y las serializa bajo el estándar internacional RFC 7807 Problem Details (`400`, `401`, `403`, `404`, `409`, `423`).
   - `RabbitMqConfig.java`: Declara el Topic Exchange `auth.events`, colas durables, enrutamiento y cola de mensajes muertos (DLQ).
   - `OpenApiConfig.java`: Configuración de Swagger 3 / OpenAPI 3.0 con esquema de seguridad Bearer JWT.
   - `Flyway SQL Migrations` (`resources/db/migration/V1__init_schema.sql`): Versionamiento automatizado del esquema relacional `styleradar_auth`.

### Estructura Oficial de Paquetes en `src/main/java/`

```text
src/main/java/edu/dosw/styleradar/auth/
├── AuthApplication.java                     <-- Punto de entrada Spring Boot (ms-auth)
├── domain/                                  <-- NÚCLEO DE DOMINIO (Cero dependencias externas)
│   ├── model/                               <-- Modelos y enumeraciones de dominio (Rol)
│   ├── event/                               <-- Definición de Eventos de Dominio
│   ├── exception/                           <-- Excepciones de reglas de negocio
│   └── port/
│       ├── in/                              <-- Input Ports (Interfaces de casos de uso)
│       └── out/                             <-- Output Ports (Interfaces para repositorios, JWT y broker)
├── application/                             <-- CAPA DE APLICACIÓN (Casos de Uso y Servicios)
│   ├── dto/
│   │   ├── request/                         <-- DTOs de entrada (LoginRequestDTO, RegistroAuthRequestDTO, RefreshTokenRequestDTO, VerificarOtpRequestDTO, ReenviarOtpRequestDTO)
│   │   ├── response/                        <-- DTOs de salida (LoginResponseDTO, UsuarioAuthResponseDTO, OtpMensajeResponseDTO, ErrorResponseDTO)
│   │   └── event/                           <-- Payloads de eventos de integración
│   ├── mapper/
│   │   ├── in/                              <-- Mappers de entrada REST/AMQP a Dominio
│   │   └── out/                             <-- Mappers de salida Dominio a Persistencia/Eventos
│   ├── service/
│   │   └── impl/                            <-- Servicios de aplicación e implementaciones (<Nombre>Impl)
│   └── usecase/                             <-- Implementación de casos de uso (<Nombre>UseCaseImpl)
└── infrastructure/                          <-- CAPA DE INFRAESTRUCTURA (Adaptadores)
    ├── adapter/
    │   ├── in/
    │   │   ├── rest/                        <-- Controladores REST (AuthController) y contrato OpenAPI (doc/AuthApi)
    │   │   ├── amqp/                        <-- Consumidores de eventos RabbitMQ
    │   │   └── websocket/                   <-- Adaptadores de entrada en tiempo real
    │   └── out/
    │       ├── persistence/                 <-- Entidades SQL (entity/) y Repositorios JPA (repository/)
    │       ├── security/                    <-- Proveedor JWT (JJWT) y cifrado BCrypt
    │       ├── messaging/                   <-- Productores de eventos RabbitMQ
    │       └── external/                    <-- Clientes de servicios externos y Redis
    ├── config/                              <-- Configuración Spring (SwaggerConfig, Seguridad)
    └── exception/                           <-- Manejo centralizado de errores HTTP (GlobalExceptionHandler)
```

---

<a id="4-endpoints-y-enlace-a-swagger"></a>

## <img src="https://img.shields.io/badge/04-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 4. ENDPOINTS Y ENLACE A SWAGGER

La documentación interactiva OpenAPI 3.0 y los contratos de ejecución se encuentran desplegados y clasificados por entorno:

### Matriz Oficial de Enlaces por Entorno (PROD, QA, LOCAL)

| Entorno | Estado | Propósito | URL Base del Servicio | Swagger OpenAPI UI | Health Check |
| :--- | :---: | :--- | :--- | :--- | :--- |
| **Producción (PROD)** | Active | Despliegue productivo de StyleRadar | `https://styleradar-backend-prod.azurewebsites.net` | [Swagger UI PROD](https://styleradar-backend-prod.azurewebsites.net/swagger-ui/index.html) | [`/actuator/health`](https://styleradar-backend-prod.azurewebsites.net/actuator/health) |
| **Calidad / Pruebas (QA)** | Active | Pruebas integradas de Sprint y QA | `https://styleradar-backend-qa.azurewebsites.net` | [Swagger UI QA](https://styleradar-backend-qa.azurewebsites.net/swagger-ui/index.html) | [`/actuator/health`](https://styleradar-backend-qa.azurewebsites.net/actuator/health) |
| **Desarrollo Local (DEV)** | Local | Entorno de desarrollo local en máquina | `http://localhost:8081` | [Swagger UI Local](http://localhost:8081/swagger-ui.html) | [`/actuator/health`](http://localhost:8081/actuator/health) |

* **Especificación OpenAPI (Contrato JSON Schema):** `http://localhost:8081/v3/api-docs` (o `/v3/api-docs` en los dominios de PROD y QA).


<div align="center">
  <table width="100%" style="margin: 18px 0; border: 2px dashed #5600EF; border-radius: 8px; background-color: #FBFBFE;">
    <tr>
      <td align="center" style="padding: 22px;">
        <p>
          <img src="https://img.shields.io/badge/ESPACIO_DE_CAPTURA-EVIDENCIA_01-5600EF?style=for-the-badge" alt="Evidencia 01" /><br />
          <strong><font color="#5600EF" size="3">DOCUMENTACIÓN SWAGGER OPENAPI UI ACTIVA</font></strong>
        </p>
        <p>
          <sub>Pega aquí la captura de pantalla de la interfaz interactiva corriendo en <code>http://localhost:8081/swagger-ui.html</code> mostrando los endpoints del controlador.</sub>
        </p>
        <p>
<!-- Al generar tu captura, descomenta la siguiente línea y guarda la imagen en la ruta indicada: -->
<!-- <img src="docs/evidencias/swagger-ui.png" width="90%" alt="Evidencia Swagger UI" /><br /> -->
          <code>[ Pegar captura de Swagger UI aquí · docs/evidencias/swagger-ui.png ]</code>
        </p>
      </td>
    </tr>
  </table>
</div>

### Tabla de Endpoints Expuestos

| Método | Endpoint | Descripción | Rol Requerido | Códigos HTTP |
| :---: | :--- | :--- | :--- | :---: |
| `POST` | `/api/v1/auth/login` | Inicio de sesión unificado multi-rol y emisión de `accessToken` + `refreshToken` (`SR-RF-02`) | Público | `200`, `400`, `401`, `403` |
| `POST` | `/api/v1/auth/registro` | Registro de comprador con contraseña `BCrypt`, validación de Habeas Data y disparo de OTP (`SR-RF-01`, `SR-RNF-02`) | Público | `201`, `400`, `409` |
| `POST` | `/api/v1/auth/refresh` | Renovación de `accessToken` mediante `refreshToken` vigente con rotación segura (`SR-RF-02`, `SR-RNF-01`) | Público (Refresh Token) | `200`, `400`, `401`, `403` |
| `POST` | `/api/v1/auth/logout` | Cierre de sesión e invalidación inmediata del token JWT en lista negra (`SR-RNF-01`, Deuda Técnica #2) | Autenticado (`Bearer JWT`) | `204`, `401` |
| `POST` | `/api/v1/auth/otp/verificar` | Verificación de código OTP de 6 dígitos (TTL 5 minutos, máximo 3 intentos) (`SR-RF-92`) | Público | `200`, `400`, `410`, `429` |
| `POST` | `/api/v1/auth/otp/reenviar` | Invalidación del OTP anterior y generación de nuevo código OTP de 6 dígitos (`SR-RF-92`) | Público | `200`, `400`, `404`, `429` |
| `GET` | `/actuator/health` | Health Check y estado operativo del microservicio | Público | `200` |

<details>
<summary><b>Click para ver Ejemplos Interactivos de Request y Response JSON (Payloads)</b></summary>

<br>

#### Ejemplo Request (`POST /api/v1/auth/login`)

```bash
curl -X POST "http://localhost:8081/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "correo": "camila.torres@correo.com",
    "password": "Password123*"
  }'
```

#### Ejemplo Response Exitoso (`200 OK`)

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwicm9sZXMiOlsiQ09NUFJBRE9SIl19.signature",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2gifQ.signature",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "usuario": {
    "id": 1,
    "nombre": "Camila Torres",
    "correo": "camila.torres@correo.com",
    "roles": ["COMPRADOR"],
    "otpVerificado": true
  }
}
```

#### Ejemplo Request (`POST /api/v1/auth/otp/verificar`)

```bash
curl -X POST "http://localhost:8081/api/v1/auth/otp/verificar" \
  -H "Content-Type: application/json" \
  -d '{
    "correo": "camila.torres@correo.com",
    "codigoOtp": "482910"
  }'
```

#### Ejemplo Response Exitoso (`200 OK`)

```json
{
  "correo": "camila.torres@correo.com",
  "mensaje": "Código OTP verificado exitosamente. Cuenta activada.",
  "expiraEnSegundos": 0,
  "intentosRestantes": 3
}
```

</details>

---

<a id="5-eventos-que-publica-y-consume"></a>

## <img src="https://img.shields.io/badge/05-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 5. EVENTOS QUE PUBLICA Y CONSUME

La comunicación asíncrona entre microservicios se orquesta a través de **RabbitMQ 3.12** utilizando Topic Exchanges durables.

* **Exchange Principal de Dominio:** `styleradar.auth.events` / `styleradar.events` (`topic`, durable)
* **Exchange de Auditoría:** `styleradar.audit` (`fanout`, durable)
* **Dead Letter Exchange (DLX):** `styleradar.dlx`

### Matriz de Eventos AMQP

| Tipo | Routing Key | Cola (Queue) | Microservicio Emisor / Receptor | Cuándo se Dispara / Acción |
| :---: | :--- | :--- | :--- | :--- |
| **PUBLICA** | `auth.usuario.registrado` | N/A (Exchange) | `ms-auth` &rarr; `ms-usuarios`, `ms-notificaciones` | Se publica al registrar un comprador (`SR-RF-01`) para sincronizar su perfil y disparar bienvenida. |
| **PUBLICA** | `auth.otp.generado` | N/A (Exchange) | `ms-auth` &rarr; `ms-notificaciones` | Se publica al generar o reenviar el código OTP de 6 dígitos (`SR-RF-92`) con vigencia de 5 minutos. |
| **PUBLICA** | `auth.sesion.revocada` | N/A (Exchange) | `ms-auth` &rarr; `api-gateway` | Notifica la invalidación de un token (`jti`) tras logout o bloqueo de seguridad (`SR-RNF-01`). |
| **CONSUME** | `usuario.estado.cambiado` | `q.auth.usuario.estado` | `ms-usuarios` &rarr; `ms-auth` | Bloquea el inicio de sesión y revoca sesiones activas cuando una cuenta pasa a `INACTIVO` o `BLOQUEADO` (`SR-RF-66`). |
| **CONSUME** | `tienda.rol.asignado` | `q.auth.tienda.rol` | `ms-tiendas` &rarr; `ms-auth` | Asigna el rol `ADMIN_ALMACEN` o `REPRESENTANTE_FUNDACION` al usuario vinculado (`SR-RF-62`, `SR-RF-63`). |

<details>
<summary><b>Click para ver Esquema JSON del Evento Publicado (con Idempotencia)</b></summary>

<br>

```json
{
  "eventId": "018e3a2b-3333-7000-8000-000000000001",
  "eventType": "auth.otp.generado",
  "timestamp": "2026-11-01T14:30:00Z",
  "producer": "ms-auth",
  "correlationId": "corr-auth-otp-0001",
  "data": {
    "usuarioId": 1,
    "correo": "camila.torres@correo.com",
    "codigoOtp": "482910",
    "ttlSegundos": 300,
    "maxIntentos": 3,
    "version": 1
  }
}
```

</details>

---

<a id="6-tecnologías-y-dependencias"></a>

## <img src="https://img.shields.io/badge/06-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 6. TECNOLOGÍAS Y DEPENDENCIAS

Esta sección presenta el análisis de tecnologías del microservicio `ms-auth` dentro del ecosistema StyleRadar. Cada elección tecnológica responde a criterios de integridad de seguridad, baja latencia en validación de sesiones, aislamiento de datos y desacoplamiento asíncrono.

### Tabla Completa de Análisis de Tecnologías del Servicio

La siguiente matriz consolida las cuatro decisiones tecnológicas estructurales del microservicio `ms-auth` y su relación con los demás componentes del Paquete 1 de Identidad gestionado por el equipo CADche:

| Servicio | Equipo | ¿BD o no? | Tipo de BD y Motor | Frameworks y Lenguaje | Broker de Mensajería | Justificación Técnica de cada Decisión |
| :--- | :---: | :---: | :--- | :--- | :--- | :--- |
| **`ms-auth`**<br>Puerto `8081` | **CADche** | **Sí** | **Híbrida: Relacional + Clave-Valor en Memoria**<br>`PostgreSQL 16` + `Redis 7` | `Java 21 LTS`, `Spring Boot 3.2.12`, `Spring Security 6`, `Spring Data JPA`, `JJWT 0.12.6`, `Flyway`, `MapStruct` | **RabbitMQ 3.12+**<br>Protocolo `AMQP 0-9-1`<br>Exchange `styleradar.auth.events` | **BD:** Persistencia crítica de credenciales, roles, consentimiento legal y sesiones.<br>**Tipo BD:** PostgreSQL garantiza transacciones ACID en el registro de identidad y auditoría; Redis ofrece expiración automática por TTL para códigos OTP y revocación inmediata de JWT.<br>**Frameworks:** Spring Security y JJWT estandarizan el cifrado BCrypt y la firma criptográfica sin estado.<br>**Broker:** RabbitMQ desacopla el envío de correos OTP y propaga eventos de registro sin bloquear el hilo HTTP. |
| **`ms-usuarios`**<br>Puerto `8082` | **CADche** | **Sí** | **Híbrida: Relacional + Documental Nativo**<br>`PostgreSQL 16` con soporte `JSONB` + `Redis 7` | `Java 21 LTS`, `Spring Boot 3.2.12`, `Spring Data JPA`, `Bean Validation`, `Flyway`, `MapStruct` | **RabbitMQ 3.12+**<br>Protocolo `AMQP 0-9-1`<br>Exchange `styleradar.usuarios.events` | **BD:** Almacena perfiles de comprador, tallas, estilos e historial de estados.<br>**Tipo BD:** Modelo relacional para el perfil estructurado y columnas `JSONB` indexadas con GIN para filtros flexibles de búsquedas guardadas.<br>**Frameworks:** Java Records y Bean Validation previenen inyecciones y expresiones regulares vulnerables.<br>**Broker:** Sincroniza la creación del perfil tras el registro en `ms-auth` y propaga cambios de preferencias hacia `ms-busqueda`. |
| **`ms-tiendas`**<br>Puerto `8083` | **CADche** | **Sí** | **Relacional + Geoespacial + Blob Storage**<br>`PostgreSQL 16` + `Redis 7` + `Azure Blob Storage` | `Java 21 LTS`, `Spring Boot 3.2.12`, `Spring Data JPA`, `Flyway`, `MapStruct` | **RabbitMQ 3.12+**<br>Protocolo `AMQP 0-9-1`<br>Exchange `styleradar.tiendas.events` | **BD:** Resguarda información jurídica por NIT, sedes georreferenciadas, horarios y dictámenes de verificación.<br>**Tipo BD:** PostgreSQL asegura integridad referencial entre tiendas, administradores y fotografías, mientras el almacenamiento de objetos aloja archivos PDF del RUT e imágenes de fachada.<br>**Frameworks:** Soporte nativo para carga multipart y cálculo geodésico Haversine en el dominio.<br>**Broker:** Notifica la aprobación de almacenes y la asignación de roles comerciales hacia `ms-auth` y `ms-catalogo`. |

---

### Matriz Detallada del Stack Tecnológico y Alternativas Evaluadas

| Capa / Componente | Tecnología Seleccionada | Versión | Alternativas Descartadas | Justificación Técnica de Elección y Descarte |
| :--- | :--- | :--- | :--- | :--- |
| **Lenguaje Core** | Java LTS | `17 / 21 LTS` | Node.js / TypeScript, Python 3.12 | Java 21 aporta tipado estático estricto, `Records` inmutables ideales para objetos de transferencia y eventos, recolección de basura ZGC de baja latencia y soporte nativo para hilos virtuales. Se descartó Node.js y Python para el núcleo de seguridad por el riesgo de errores de tipado en tiempo de ejecución y menor rendimiento en operaciones criptográficas intensivas como `BCrypt`. |
| **Framework Empresarial** | Spring Boot | `3.2.12` | Quarkus, Micronaut, NestJS | Provee integración madura con Spring Security, Spring Data, validación declarativa y observabilidad mediante Actuator. El equipo cuenta con dominio previo del ecosistema Spring, lo que reduce la curva de aprendizaje frente a Quarkus o Micronaut durante los cuatro sprints del proyecto. |
| **Seguridad y Criptografía** | Spring Security + JJWT | `6.2.x / 0.12.6` | Auth0 / Keycloak externo, Apache Shiro | Permite implementar un flujo de autenticación a medida con control total sobre los claims del JWT, rotación de `refreshToken` y bloqueo por intentos fallidos sin incurrir en costos de licenciamiento por usuario activo ni depender de la disponibilidad de un proveedor SaaS externo. |
| **Motor de Base de Datos Relacional** | PostgreSQL | `16` | MySQL 8, Oracle XE, MongoDB | Motor relacional de código abierto con cumplimiento estricto del estándar SQL, soporte transaccional ACID completo, bloqueo a nivel de fila sin escalamiento y excelente desempeño en escrituras concurrentes de bitácoras de auditoría. Se descartó MongoDB porque las credenciales y roles exigen un esquema rígido sin duplicidad ni inconsistencias eventuales. |
| **Almacenamiento en Memoria y Caché** | Redis | `7.x` | Memcached, tablas SQL con borrado programado | Almacén clave-valor en memoria RAM con latencia inferior a un milisegundo y soporte nativo de expiración automática por clave mediante `TTL`. Resulta indispensable para caducar códigos OTP a los 300 segundos exactos y consultar la lista negra de tokens revocados en cada petición sin sobrecargar PostgreSQL con tareas programadas de limpieza. |
| **Control de Migraciones SQL** | Flyway | `10.20.1` | Liquibase, `ddl-auto: update` de Hibernate | Gestiona la evolución del esquema `styleradar_auth` mediante scripts SQL puros, versionados y verificados por suma de comprobación en el arranque. Se prohibió `ddl-auto: update` en entornos productivos porque puede alterar tablas de forma impredecible y no deja trazabilidad de cambios. |
| **Broker de Mensajería** | RabbitMQ | `3.12+` | Apache Kafka, Redis Pub/Sub, REST síncrono | Implementa el protocolo estándar `AMQP 0-9-1` con enrutamiento flexible mediante Topic Exchanges, confirmación de entrega al productor, reintentos y colas de mensajes fallidos. Se descartó Apache Kafka por su alto consumo de memoria y complejidad operativa innecesaria para el volumen de eventos de trece microservicios; se descartó Redis Pub/Sub porque no persiste mensajes si el consumidor está temporalmente caído. |
| **Mapeo de Objetos** | MapStruct + Lombok | `1.5.5.Final / 1.18.42` | ModelMapper, Dozer, mapeo manual repetitivo | MapStruct genera código Java de conversión entre DTOs, modelos de dominio y entidades JPA durante la fase de compilación. A diferencia de ModelMapper, no utiliza reflexión en tiempo de ejecución, garantizando velocidad de ejecución nativa y detección temprana de errores de mapeo al compilar. |
| **Contrato y Documentación API** | SpringDoc OpenAPI | `2.3.0` | Springfox, documentación estática manual | Genera dinámicamente la especificación OpenAPI 3.0 y la interfaz interactiva Swagger UI a partir de las anotaciones de los controladores y esquemas de validación, asegurando que la documentación nunca quede desactualizada respecto al código fuente. |
| **Calidad, Pruebas y Cobertura** | JUnit 5, Mockito, Testcontainers, JaCoCo | `5.10.x / 1.19.8 / 0.8.11` | Base de datos H2 en memoria exclusiva, pruebas manuales | Permite validar las reglas de negocio del dominio en aislamiento con Mockito y verificar las consultas reales de PostgreSQL mediante contenedores efímeros con Testcontainers, manteniendo una cobertura verificable por JaCoCo igual o superior al 80%. |

---

### Análisis Detallado de las Decisiones Tecnológicas

#### 6.1 ¿Requiere Base de Datos o no?

**Decisión: Sí requiere base de datos propia.**

Aunque el procesamiento de las peticiones HTTP en `ms-auth` sigue un diseño sin estado basado en tokens JWT para facilitar el escalado horizontal de instancias, el servicio es intrínsecamente con estado en su capa de persistencia. `ms-auth` constituye la única fuente de verdad de la identidad digital dentro de la plataforma StyleRadar.

El servicio necesita persistir de forma duradera e independiente cinco conjuntos de información crítica:
1. **Credenciales maestras y estado de seguridad:** El correo electrónico único, el hash criptográfico de la contraseña generada con `BCrypt`, el rol de acceso asignado, el contador de intentos fallidos de inicio de sesión y las marcas de bloqueo temporal de la cuenta.
2. **Trazabilidad legal y regulatoria:** La fecha, hora e identificador de aceptación de los términos y condiciones junto con la política de tratamiento de datos personales exigida por la Ley 1581 de 2012 en Colombia.
3. **Ciclo de vida de sesiones y tokens de refresco:** El registro de sesiones emitidas, su fecha de vencimiento y su estado de revocación para impedir la reutilización de tokens comprometidos.
4. **Bitácora inmutable de auditoría de seguridad:** El historial de inicios de sesión exitosos, intentos fallidos, cambios de contraseña y bloqueos administrativos para análisis forense y control de fraude.
5. **Estado efímero de verificación y revocación:** Los desafíos de código OTP de segundo factor y los identificadores de tokens invalidados antes de su expiración natural.

Bajo el patrón **Database-per-Service**, ninguna otra base de datos del ecosistema puede almacenar ni consultar directamente las tablas de `styleradar_auth`. Delegar estos datos a otro microservicio rompería el aislamiento del contexto delimitado de seguridad y expondría los hashes de contraseñas a componentes externos.

#### 6.2 Tipo de Base de Datos y Justificación del Modelo de Persistencia

**Decisión: Persistencia políglota híbrida compuesta por una Base de Datos Relacional en PostgreSQL 16 y una Base de Datos No Relacional Clave-Valor en Memoria en Redis 7.**

La naturaleza del dominio de autenticación presenta dos patrones de acceso a datos con requisitos opuestos de durabilidad y latencia, lo que justifica combinar dos motores complementarios en lugar de forzar un único almacenamiento:

* **Por qué una Base de Datos Relacional en PostgreSQL 16 como motor principal:**
  Los datos de identidad, credenciales, roles y auditoría tienen una estructura altamente definida e invariable donde la consistencia inmediata es innegociable. Cuando un usuario se registra o cambia su contraseña, la operación debe cumplir las propiedades **ACID**:
  * **Atomicidad:** La creación de la cuenta en `usuarios_auth`, el registro de la aceptación legal de Habeas Data y la inserción del evento de auditoría en `auditoria_accesos` deben confirmarse como una sola unidad transaccional o revertirse por completo si ocurre un fallo.
  * **Consistencia e Integridad:** Las restricciones de unicidad sobre el correo electrónico y las restricciones de verificación sobre los roles y estados de cuenta evitan a nivel de motor que existan cuentas duplicadas o con permisos corruptos, incluso ante peticiones simultáneas.
  * **Aislamiento y Durabilidad:** El control de concurrencia multiversión de PostgreSQL permite actualizar el contador de intentos fallidos de inicio de sesión sin bloquear las lecturas concurrentes de otros usuarios, garantizando que ningún cambio confirmado se pierda ante un reinicio del contenedor.

* **Por qué una Base de Datos No Relacional Clave-Valor en Redis 7 como motor complementario:**
  Operaciones como el envío de un código OTP de 6 dígitos con vigencia exacta de 5 minutos o la invalidación inmediata de un token JWT al cerrar sesión son datos transitorios de alta frecuencia de lectura. Si estos registros se guardaran únicamente en tablas relacionales, sería necesario ejecutar procesos programados de barrido y eliminación cada minuto, generando fragmentación en disco y carga innecesaria de entrada y salida. Redis resuelve este problema almacenando pares clave-valor directamente en memoria RAM con un tiempo de vida automático asignado en el momento de la escritura:
  * **Claves `otp:challenge:{correo}` con TTL de 300 segundos:** El propio motor de Redis elimina el código OTP exactamente al cumplirse los cinco minutos sin intervención de la aplicación.
  * **Claves `auth:blacklist:{jti}` con TTL igual al tiempo restante del token:** Permite verificar en menos de un milisegundo si un token ha sido revocado antes de autorizar una operación sensible, desapareciendo automáticamente de la memoria cuando el token habría expirado de forma natural.

#### 6.3 Frameworks, Lenguaje y Herramientas de Desarrollo

**Decisión: Ecosistema Java 17/21 LTS con Spring Boot 3.2.12, Spring Security 6, Spring Data JPA, Flyway y MapStruct bajo Arquitectura Hexagonal.**

Cada herramienta del stack cumple una responsabilidad técnica precisa sin invadir la lógica de negocio:

* **Java 21 LTS y Arquitectura Hexagonal:** El núcleo del servicio, compuesto por los paquetes `domain/model` y `domain/usecase`, se escribe en Java puro sin importar anotaciones de persistencia ni del framework web. Los objetos de transferencia de entrada y salida se modelan con `Java Records`, lo que garantiza inmutabilidad desde el momento en que la petición JSON ingresa al controlador. La inyección de los casos de uso se centraliza de forma explícita en la clase de configuración `BeanConfig`, manteniendo el dominio 100% libre de acoplamiento con Spring.
* **Spring Boot 3.2.12 y Spring Security 6:** Proveen la infraestructura robusta para los adaptadores de entrada y salida. Spring Security gestiona la cadena de filtros HTTP sin estado, la protección contra ataques de fuerza bruta y la codificación segura de contraseñas mediante `BCryptPasswordEncoder` con factor de costo configurable, aplicando sal criptográfica aleatoria en cada hash.
* **JJWT 0.12.6:** Librería especializada en el estándar RFC 7519 para la generación, firma digital y análisis de tokens JWT. Permite incrustar en el payload los atributos esenciales de identidad como el identificador del usuario, el correo y la lista de roles, permitiendo que el API Gateway y los demás microservicios autoricen peticiones localmente sin consultar a `ms-auth` en cada clic del usuario.
* **Spring Data JPA, Hibernate y Flyway:** En la capa de infraestructura de salida, Spring Data JPA simplifica la implementación del adaptador `AuthJpaAdapter` mediante repositorios tipados y consultas parametrizadas que previenen ataques de inyección SQL. Flyway complementa a JPA ejecutando los scripts versionados de creación de tablas e índices de manera determinista al iniciar el servicio.
* **MapStruct 1.5.5:** Se encarga de traducir entre los DTOs expuestos en la API REST, las entidades puras del dominio y las entidades `@Entity` de la base de datos. Al generar clases de mapeo durante la compilación con Maven, ofrece el mismo rendimiento que el código escrito a mano pero evitando errores humanos al copiar atributos.

#### 6.4 Broker de Mensajería y Comunicación Orientada a Eventos

**Decisión: RabbitMQ 3.12+ mediante el protocolo estándar AMQP 0-9-1 con arquitectura de Topic Exchanges y colas durables.**

En una arquitectura de microservicios distribuidos, comunicar cambios de estado mediante llamadas HTTP síncronas encadenadas introduce acoplamiento temporal: si el servicio de correos o el servicio de perfiles tardan en responder o sufren una caída momentánea, el inicio de sesión o el registro del usuario en `ms-auth` fallaría por tiempo de espera agotado. Para eliminar este riesgo, se eligió **RabbitMQ** como bus de eventos asíncrono.

* **Justificación de RabbitMQ frente a otras alternativas:**
  RabbitMQ ofrece un modelo de enrutamiento inteligente mediante *Exchanges* de tipo `topic` que permite distribuir un mismo evento de dominio hacia múltiples colas interesadas según patrones de clave de enrutamiento `routing key`. A diferencia de Apache Kafka, cuyo diseño orientado a registros particionados de flujo continuo exige un consumo elevado de recursos de infraestructura, RabbitMQ es ligero, garantiza latencias de entrega en el orden de microsegundos a pocos milisegundos, soporta confirmaciones de recepción por mensaje individual y maneja de forma nativa el reencolamiento y las colas de mensajes muertos `Dead Letter Queues`.
* **Flujo de eventos publicados por `ms-auth`:**
  * `auth.usuario.registrado`: Cuando una cuenta nueva se persiste en PostgreSQL, `ms-auth` publica este evento en el exchange `styleradar.auth.events`. El microservicio `ms-usuarios` lo consume para inicializar el perfil base del comprador y `ms-notificaciones` lo consume para enviar el correo de bienvenida, todo ello sin retrasar la respuesta `201 Created` al cliente.
  * `auth.otp.solicitado`: Al generarse un código OTP para verificación en dos pasos o recuperación de contraseña, `ms-auth` publica el evento para que `ms-notificaciones` despache el correo transaccional de forma asíncrona, resolviendo el cuello de botella de latencia SMTP identificado en el análisis de deuda técnica.
  * `auth.cuenta.bloqueada`: Notifica de manera inmediata al ecosistema cuando una cuenta supera el umbral de intentos fallidos o recibe una suspensión de seguridad.
* **Flujo de eventos consumidos por `ms-auth`:**
  * `usuario.estado.cambiado` y `tienda.rol.asignado`: `ms-auth` escucha a través de sus colas durables las decisiones administrativas originadas en `ms-usuarios` y `ms-tiendas` para revocar sesiones activas o actualizar los permisos efectivos del usuario, asegurando consistencia eventual con control de idempotencia mediante el identificador único de evento `eventId`.

---

<a id="7-cómo-ejecutar-local-y-con-docker"></a>

## <img src="https://img.shields.io/badge/07-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 7. CÓMO EJECUTAR LOCAL Y CON DOCKER

### Prerrequisitos de Entorno

* **Java Development Kit (JDK):** Versión 17 o 21 LTS instalada (`java -version`).
* **Apache Maven:** Versión 3.9+ instalada (`mvn -version`).
* **Docker Engine & Docker Compose:** Versión 24+ en ejecución (`docker --version`).

### Ejecución Local con Maven (Perfil Dev / Test)

```bash
# 1. Clonar el repositorio
git clone https://github.com/StyleRadar1/CADCHE-Authentication-Service.git
cd CADCHE-Authentication-Service

# 2. Levantar servicios de infraestructura en Docker (PostgreSQL, Redis, RabbitMQ)
docker compose up -d postgres rabbitmq redis

# 3. Ejecutar el microservicio localmente con PostgreSQL (perfil dev)
mvn clean spring-boot:run -Dspring-boot.run.profiles=dev

# Alternativa rápida sin PostgreSQL externo (usando configuración en memoria de test)
mvn spring-boot:run -Dspring-boot.run.useTestClasspath=true -Dspring-boot.run.profiles=test
```

### Ejecución Contenerizada Completa con Docker

<details>
<summary><b>Click para ver Instrucciones de Docker y docker-compose.yml de Ejemplo</b></summary>

<br>

```bash
# Compilar y empaquetar el JAR ejecutable omitiendo tests
mvn clean package -DskipTests

# Construir la imagen OCI local
docker build -t styleradar/ms-auth:v1.0 .

# Ejecutar el contenedor conectado a la red compartida de StyleRadar
docker run -d \
  --name ms-auth \
  --network styleradar-net \
  -p 8081:8081 \
  --env-file .env \
  styleradar/ms-auth:v1.0
```

#### Archivo `docker-compose.yml` de Soporte

```yaml
version: '3.8'

networks:
  styleradar-net:
    driver: bridge

services:
  ms-auth:
    build: .
    container_name: ms-auth
    restart: always
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SERVER_PORT=8081
      - DB_URL=jdbc:postgresql://postgres:5432/styleradar_auth
      - DB_USERNAME=postgres
      - DB_PASSWORD=postgres
      - JPA_DDL_AUTO=validate
      - FLYWAY_ENABLED=true
      - RABBITMQ_HOST=rabbitmq
      - RABBITMQ_PORT=5672
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    depends_on:
      - postgres
      - rabbitmq
      - redis
    networks:
      - styleradar-net

  postgres:
    image: postgres:16-alpine
    container_name: sr-postgres-auth
    environment:
      POSTGRES_DB: styleradar_auth
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5431:5432"
    networks:
      - styleradar-net

  redis:
    image: redis:7-alpine
    container_name: sr-redis-auth
    ports:
      - "6379:6379"
    networks:
      - styleradar-net

  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    container_name: sr-rabbitmq-auth
    ports:
      - "5672:5672"
      - "15672:15672"
    networks:
      - styleradar-net
```

</details>

### Comprobación de Salud (Health Check)

```bash
curl -s http://localhost:8081/actuator/health | jq .
# Esperado: {"status":"UP"}
```

<div align="center">
  <table width="100%" style="margin: 18px 0; border: 2px dashed #5600EF; border-radius: 8px; background-color: #FBFBFE;">
    <tr>
      <td align="center" style="padding: 22px;">
        <p>
          <img src="https://img.shields.io/badge/ESPACIO_DE_CAPTURA-EVIDENCIA_02-5600EF?style=for-the-badge" alt="Evidencia 02" /><br />
          <strong><font color="#5600EF" size="3">CONTENEDOR OCI DOCKER EN EJECUCIÓN (docker compose ps)</font></strong>
        </p>
        <p>
          <sub>Pega aquí la captura de terminal ejecutando <code>docker compose ps</code> mostrando el contenedor del microservicio y sus servicios de persistencia y broker en estado healthy.</sub>
        </p>
        <p>
<!-- Al generar tu captura, descomenta la siguiente línea y guarda la imagen en la ruta indicada: -->
<!-- <img src="docs/evidencias/docker-compose-ps.png" width="90%" alt="Docker Compose PS" /><br /> -->
          <code>[ Pegar captura de Docker compose ps aquí · docs/evidencias/docker-compose-ps.png ]</code>
        </p>
      </td>
    </tr>
  </table>
</div>


---

<a id="8-variables-de-entorno"></a>

## <img src="https://img.shields.io/badge/08-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 8. VARIABLES DE ENTORNO

Todas las configuraciones sensibles y dependientes de entorno se externalizan mediante variables de sistema conformes al estándar **Twelve-Factor App**.

| Variable | Descripción | Valor por Defecto (`dev`) | Requerido en Prod |
| :--- | :--- | :--- | :---: |
| `SERVER_PORT` | Puerto de escucha HTTP del microservicio | `8081` | Sí |
| `SPRING_PROFILES_ACTIVE` | Perfil de configuración Spring Boot activo | `dev` | Sí |
| `DB_URL` | URL JDBC de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/styleradar_auth` | Sí |
| `DB_USERNAME` | Usuario de conexión a la base de datos | `postgres` | Sí |
| `DB_PASSWORD` | Contraseña del usuario de base de datos | `postgres` | Sí |
| `JPA_DDL_AUTO` | Estrategia de validación de esquema Hibernate | `validate` | Sí |
| `FLYWAY_ENABLED` | Ejecución automática de migraciones Flyway | `false` (`true` en prod) | Sí |
| `JWT_SECRET` | Clave secreta o par de llaves para firma de JWT | `<SECRET_KEY_256_BITS>` | Sí |
| `JWT_ACCESS_EXPIRATION` | Tiempo de expiración del `accessToken` en segundos | `3600` | Sí |
| `JWT_REFRESH_EXPIRATION` | Tiempo de expiración del `refreshToken` en segundos | `604800` | Sí |
| `OTP_EXPIRATION_SECONDS` | Tiempo de vida (TTL) del código OTP de 6 dígitos | `300` | Sí |
| `OTP_MAX_ATTEMPTS` | Número máximo de intentos fallidos de validación OTP | `3` | Sí |
| `REDIS_HOST` | Hostname del servidor de caché Redis (OTP / Blacklist) | `localhost` | Sí |
| `REDIS_PORT` | Puerto de conexión a Redis | `6379` | Sí |
| `RABBITMQ_HOST` | Hostname del broker de mensajería RabbitMQ | `localhost` | Sí |
| `RABBITMQ_PORT` | Puerto AMQP de RabbitMQ | `5672` | Sí |
| `RABBITMQ_USERNAME` | Usuario de conexión a RabbitMQ | `guest` | Sí |
| `RABBITMQ_PASSWORD` | Contraseña de conexión a RabbitMQ | `guest` | Sí |

<details>
<summary><b>Click para copiar la Plantilla de Archivo .env.example</b></summary>

<br>

```dotenv
# ==============================================================================
# STYLERADAR · ms-auth · VARIABLES DE ENTORNO LOCALES (.env)
# ==============================================================================
SERVER_PORT=8081
SPRING_PROFILES_ACTIVE=dev

# Persistencia PostgreSQL
DB_URL=jdbc:postgresql://localhost:5432/styleradar_auth
DB_USERNAME=postgres
DB_PASSWORD=postgres
JPA_DDL_AUTO=validate
FLYWAY_ENABLED=false

# Seguridad JWT y OTP
JWT_SECRET=change_this_secret_in_production_minimum_32_characters
JWT_ACCESS_EXPIRATION=3600
JWT_REFRESH_EXPIRATION=604800
OTP_EXPIRATION_SECONDS=300
OTP_MAX_ATTEMPTS=3

# Caché Redis (OTP & JWT Revocation List)
REDIS_HOST=localhost
REDIS_PORT=6379

# Event Broker RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
```

</details>

---

<a id="9-pruebas-y-cobertura"></a>

## <img src="https://img.shields.io/badge/09-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 9. PRUEBAS Y COBERTURA (≥ 85%)

El proyecto exige una cobertura de código mínima del **85%** verificada automáticamente mediante el plugin de **JaCoCo** durante la fase de compilación.

### Comandos de Ejecución de Pruebas

```bash
# Ejecutar todas las pruebas unitarias, de contrato OpenAPI y de integración
mvn clean verify

# Generar únicamente el reporte HTML de JaCoCo
mvn jacoco:report
```

* **Ruta del Reporte HTML:** `target/site/jacoco/index.html`

<div align="center">
  <table width="100%" style="margin: 18px 0; border: 2px dashed #5600EF; border-radius: 8px; background-color: #FBFBFE;">
    <tr>
      <td align="center" style="padding: 22px;">
        <p>
          <img src="https://img.shields.io/badge/ESPACIO_DE_CAPTURA-EVIDENCIA_03-5600EF?style=for-the-badge" alt="Evidencia 03" /><br />
          <strong><font color="#5600EF" size="3">REPORTE CONSOLIDADO DE COBERTURA JACOCO (≥ 85%)</font></strong>
        </p>
        <p>
          <sub>Pega aquí la captura del reporte HTML (<code>target/site/jacoco/index.html</code>) evidenciando el cumplimiento de la cobertura mínima obligatoria (≥ 85%).</sub>
        </p>
        <p>
<!-- Al generar tu captura, descomenta la siguiente línea y guarda la imagen en la ruta indicada: -->
<!-- <img src="docs/evidencias/cobertura-jacoco.png" width="90%" alt="Reporte JaCoCo" /><br /> -->
          <code>[ Pegar captura de JaCoCo ≥ 85% aquí · docs/evidencias/cobertura-jacoco.png ]</code>
        </p>
      </td>
    </tr>
  </table>
</div>


### Pirámide de Pruebas Implementada

| Nivel de Prueba | Alcance | Tecnologías Utilizadas | Ubicación en el Código | Cobertura Meta |
| :--- | :--- | :--- | :--- | :---: |
| **Pruebas Unitarias** | Lógica de Dominio y Casos de Uso aislados | JUnit 5, Mockito, AssertJ | `src/test/java/edu/dosw/styleradar/auth/domain/`, `application/` | ≥ 90% |
| **Pruebas de Integración** | Repositorios JPA y Adaptadores de Seguridad | Spring Boot Test, Testcontainers | `src/test/java/edu/dosw/styleradar/auth/infrastructure/` | ≥ 85% |
| **Pruebas de Contrato** | Verificación de contratos OpenAPI 3 y Endpoints REST | MockMvc, SpringDoc OpenAPI Test (`SwaggerOpenApiTest`) | `src/test/java/edu/dosw/styleradar/auth/infrastructure/adapter/in/rest/` | ≥ 85% |

> [!IMPORTANT]
> **Criterio de Fallo en CI:** El archivo `pom.xml` contempla la regla `<limit><minimum>0.85</minimum></limit>`. Si la cobertura total consolidada es inferior al 85%, el comando `mvn verify` fallará con error y el pipeline bloqueará el Pull Request.

---

<a id="10-cicd-con-el-badge-del-pipeline"></a>

## <img src="https://img.shields.io/badge/10-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 10. CI/CD CON EL BADGE DEL PIPELINE

El ciclo de vida del software implementa integración continua y entrega continua automatizada mediante **GitHub Actions / Azure Pipelines**:

### Matriz de Pipelines por Entorno (PROD y QA)

| Pipeline | Entorno Destino | Desencadenador (Trigger) | Quality Gate Exigido | Badge de Estado |
| :--- | :--- | :--- | :--- | :---: |
| **CI - Staging / QA** | Calidad (`QA`) | Pull Request o Push a rama `develop` | Build OK + Tests Unitarios/Integración + JaCoCo ≥ 85% + SonarQube OK | [![QA Pipeline](https://img.shields.io/badge/Pipeline_QA-Passing-5600EF?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions) |
| **CD - Producción (PROD)** | Producción (`PROD`) | Merge a rama `main` / `master` tras validación QA | Aprobación manual + Imagen OCI desplegada en Azure App Service | [![PROD Pipeline](https://img.shields.io/badge/Pipeline_PROD-Passing-16A34A?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions) |
| **Quality Gate SonarQube** | Transversal | Ejecutado en cada build de CI | 0 Vulnerabilidades, 0 Bugs, Deuda Técnica &lt; 5% | [![Quality Gate](https://img.shields.io/badge/SonarQube-Quality_Gate_Passed-16A34A?style=for-the-badge&logo=sonarqube&logoColor=white)](https://sonarcloud.io/organizations/styleradar/projects) |

### Diagrama del Flujo Automatizado (Pipeline Stages)

```mermaid
flowchart LR
    A["Push / PR<br>(GitHub)"] --> B["Build & Compile<br>(Maven Java)"]
    B --> C["Tests & JaCoCo<br>(Coverage ≥ 85%)"]
    C --> D["Análisis SonarQube<br>(0 Vulnerabilities)"]
    D --> E["Build Docker OCI<br>(Multi-stage Build)"]
    E --> F["Deploy Cloud<br>(Azure App Service)"]

    classDef step fill:#12063A,stroke:#5600EF,stroke-width:2px,color:#FFFFFF;
    classDef pass fill:#DCFCE7,stroke:#16A34A,stroke-width:2px,color:#0C0C12;

    class A,B,E,F step;
    class C,D pass;
```

### Reglas de Calidad en SonarQube (*Quality Gate Passed*)

* **Bugs:** 0
* **Vulnerabilidades:** 0
* **Hotspots de Seguridad:** 0 revisados al 100%
* **Code Smells Críticos:** 0
* **Deuda Técnica:** < 5%

<div align="center">
  <table width="100%" style="margin: 18px 0; border: 2px dashed #5600EF; border-radius: 8px; background-color: #FBFBFE;">
    <tr>
      <td align="center" style="padding: 22px;">
        <p>
          <img src="https://img.shields.io/badge/ESPACIO_DE_CAPTURA-EVIDENCIA_04-5600EF?style=for-the-badge" alt="Evidencia 04" /><br />
          <strong><font color="#5600EF" size="3">EJECUCIÓN DEL PIPELINE CI/CD EN VERDE (BUILD & QUALITY GATE PASSED)</font></strong>
        </p>
        <p>
          <sub>Pega aquí la captura del pipeline de integración continua (Azure DevOps / GitHub Actions) finalizado con éxito en todas sus fases.</sub>
        </p>
        <p>
<!-- Al generar tu captura, descomenta la siguiente línea y guarda la imagen en la ruta indicada: -->
<!-- <img src="docs/evidencias/pipeline-ci-cd.png" width="90%" alt="Pipeline CI/CD" /><br /> -->
          <code>[ Pegar captura del Pipeline CI/CD en verde aquí · docs/evidencias/pipeline-ci-cd.png ]</code>
        </p>
      </td>
    </tr>
  </table>
</div>

---

<a id="11-ramas-y-cómo-contribuir"></a>

## <img src="https://img.shields.io/badge/11-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 11. RAMAS Y CÓMO CONTRIBUIR

El equipo sigue el modelo de trabajo **GitFlow adaptado para microservicios**. Las ramas `main` (`master`) y `develop` se encuentran protegidas y solo reciben cambios mediante Pull Requests aprobados.

### Estructura de Ramas Oficial

```text
main / master           <-- Código en producción (Desplegado en Azure PROD con CMV)
└── develop             <-- Rama de integración continua (Staging / QA)
    ├── feature/<tarea-o-HU>                  <-- Nuevas funcionalidades (ej. feature/swagger, feature/readme)
    ├── bugfix/<descripcion-corta>            <-- Corrección de defectos
    └── hotfix/<descripcion-corta>            <-- Parche urgente a main
```

### Convención de Commits (*Conventional Commits*)

Todo mensaje de commit debe escribirse en **minúsculas** y en **modo imperativo** siguiendo la estructura:

```text
<tipo>(<alcance>): <descripción corta en imperativo>

Tipos permitidos:
- feat: nueva funcionalidad (endpoints, casos de uso, etc.)
- fix: corrección de un bug
- refactor: cambios de código que no arreglan bugs ni añaden funciones (ej. reorganizar a hexagonal)
- test: agregar o corregir pruebas unitarias/integración
- docs: cambios en la documentación (README.md, Swagger, etc.)
- build: modificaciones en dependencias o archivos de construcción (pom.xml, etc.)
- ci: cambios en los pipelines de integración continua
- chore: tareas de mantenimiento general (archivos de configuración, .env.example, etc.)

Ejemplos válidos:
feat(auth): agregar soporte de verificacion y reenvio otp y revocacion de sesion
docs(auth): especificar contrato openapi 3 completo en endpoints de autenticacion
test(auth): agregar prueba de integracion para verificar contrato openapi
docs(readme): estructurar documentacion tecnica del servicio ms-auth
```

### Checklist Obligatorio para Pull Requests

- [ ] La rama parte de la versión más reciente de `develop` (o de la rama feature base acordada).
- [ ] El código compila limpiamente con `mvn clean compile`.
- [ ] Todas las pruebas unitarias e integración pasan (`mvn verify`).
- [ ] La cobertura JaCoCo es mayor o igual al 85%.
- [ ] No se introducen credenciales, contraseñas ni tokens en el código fuente.
- [ ] El PR tiene asignado al menos 1 revisor del squad antes del merge.

---

<a id="12-enlace-a-la-épica-en-jira"></a>

## <img src="https://img.shields.io/badge/12-5600EF?style=for-the-badge" height="20" align="absmiddle" /> 12. ENLACE A LA ÉPICA EN JIRA

El seguimiento de requerimientos, evidencias y artefactos de entrega se centraliza en los siguientes enlaces oficiales:

### Enlaces de Gestión, Repositorios y Evidencias

* **Épica Oficial del Microservicio (Jira):** [https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog](https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog)
* **Tablero Scrum del Squad CADche (Jira):** [https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog](https://mail-team-nyjtgqcj.atlassian.net/jira/software/projects/CADCHE/boards/75/backlog)
* **API Swagger en Producción (PROD):** [https://styleradar-backend-prod.azurewebsites.net/swagger-ui/index.html](https://styleradar-backend-prod.azurewebsites.net/swagger-ui/index.html)
* **API Swagger en Calidad (QA):** [https://styleradar-backend-qa.azurewebsites.net/swagger-ui/index.html](https://styleradar-backend-qa.azurewebsites.net/swagger-ui/index.html)
* **Colección de Pruebas Postman:** [CatrachoINXS/StyleRadar-Backend/tree/main/postman](https://github.com/CatrachoINXS/StyleRadar-Backend/tree/main/postman)
* **Contenedor OCI Oficial (Docker Hub):** [hub.docker.com/r/juanamunoz/styleradar](https://hub.docker.com/r/juanamunoz/styleradar)
* **Video de Respaldo de la Demostración:** [Demostración Funcional en Google Drive](https://drive.google.com/file/d/1W6dklNVi8Bp8LQUx1dkGrkMlly0gRjd7/view?usp=sharing)


<div align="center">
  <table width="100%" style="margin: 18px 0; border: 2px dashed #5600EF; border-radius: 8px; background-color: #FBFBFE;">
    <tr>
      <td align="center" style="padding: 22px;">
        <p>
          <img src="https://img.shields.io/badge/ESPACIO_DE_CAPTURA-EVIDENCIA_05-5600EF?style=for-the-badge" alt="Evidencia 05" /><br />
          <strong><font color="#5600EF" size="3">TABLERO SCRUM Y SEGUIMIENTO DE LA ÉPICA EN JIRA</font></strong>
        </p>
        <p>
          <sub>Pega aquí la captura del tablero de Jira mostrando la Épica, User Stories y tareas asignadas al microservicio para el sprint.</sub>
        </p>
        <p>
<!-- Al generar tu captura, descomenta la siguiente línea y guarda la imagen en la ruta indicada: -->
<!-- <img src="docs/evidencias/jira-tablero-epica.png" width="90%" alt="Tablero Jira" /><br /> -->
          <code>[ Pegar captura del Tablero de Jira aquí · docs/evidencias/jira-tablero-epica.png ]</code>
        </p>
      </td>
    </tr>
  </table>
</div>

### Requerimientos Funcionales Asignados

| Código RF | Nombre del Requerimiento | Actor Principal | Criterio de Aceptación Clave |
| :---: | :--- | :--- | :--- |
| `SR-RF-01` | Registro de usuario comprador con credenciales seguras | `COMPRADOR` | Almacena la contraseña con cifrado `BCrypt` (costo ≥ 10), valida unicidad de correo (`409 Conflict` si ya existe) y exige aceptación de Habeas Data (`SR-RNF-02`). |
| `SR-RF-02` | Inicio de sesión unificado multi-rol y renovación de token | `COMPRADOR`, `ADMIN_ALMACEN`, `REPRESENTANTE_FUNDACION`, `ADMIN_STYLERADAR` | Valida credenciales contra el hash `BCrypt`, verifica que la cuenta esté activa y emite `accessToken` + `refreshToken` firmados con los roles del usuario. |
| `SR-RF-92` | Verificación de correo electrónico con código OTP de 6 dígitos | `COMPRADOR`, `ADMIN_ALMACEN`, `REPRESENTANTE_FUNDACION` | Genera código numérico de 6 dígitos con expiración estricta de 5 minutos (`300s`) y bloquea tras 3 intentos fallidos (`429 Too Many Requests`), permitiendo reenvío. |
| `SR-RNF-01` | Revocación de sesión activa y rotación de tokens (Deuda Técnica #2) | Usuario Autenticado | Invalida de inmediato el `accessToken` (`jti`) en lista negra al ejecutar `POST /api/v1/auth/logout`, impidiendo su reutilización antes del vencimiento natural. |
| `SR-RNF-02` | Cumplimiento de Habeas Data (Ley 1581 de 2012) | `COMPRADOR` | Rechaza cualquier solicitud de registro donde `aceptaHabeasData` sea `false` o nulo con código `400 Bad Request`. |
