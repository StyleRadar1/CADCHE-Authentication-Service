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

  <p>
    <img src="https://img.shields.io/badge/BOUNDED_CONTEXT-Autenticaci%C3%B3n_e_Identidad_(IAM)-12063A?style=for-the-badge" alt="Context" />
    <img src="https://img.shields.io/badge/PUERTO_OFICIAL-8100-5600EF?style=for-the-badge" alt="Port" />
    <img src="https://img.shields.io/badge/ARQUITECTURA-HEXAGONAL-16A34A?style=for-the-badge" alt="Hexagonal" />
  </p>

  <p><i>Inicio de sesión (SR-RF-02), verificación del correo con código OTP (SR-RF-92), Google OAuth2 y revocación de sesiones.</i></p>

  <p>
    <a href="https://styleradar-auth-prod.azurewebsites.net/actuator/health">
      <img src="https://img.shields.io/badge/PROD-Azure_App_Service-16A34A?style=for-the-badge&logo=azure-app-services&logoColor=white" alt="PROD Deployment" />
    </a>
    <a href="https://styleradar-auth-qa.azurewebsites.net/swagger-ui/index.html">
      <img src="https://img.shields.io/badge/QA-Active-5600EF?style=for-the-badge&logo=azure-app-services&logoColor=white" alt="QA Deployment" />
    </a>
    <a href="https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci.yml">
      <img src="https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci.yml/badge.svg?branch=develop" alt="CI/CD" />
    </a>
    <a href="https://sonarcloud.io/project/overview?id=StyleRadar1_CADCHE-Authentication-Service">
      <img src="https://sonarcloud.io/api/project_badges/measure?project=StyleRadar1_CADCHE-Authentication-Service&metric=alert_status" alt="Quality Gate" />
    </a>
    <a href="https://sonarcloud.io/component_measures?id=StyleRadar1_CADCHE-Authentication-Service&metric=coverage">
      <img src="https://img.shields.io/badge/JaCoCo_Coverage-94.8%25-16A34A?style=for-the-badge&logo=codecov&logoColor=white" alt="Coverage" />
    </a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Java-21_LTS-12063A?style=for-the-badge&logo=openjdk&logoColor=C4B5FD" alt="Java 21 LTS" />
    <img src="https://img.shields.io/badge/Spring_Boot-3.2.12-12063A?style=for-the-badge&logo=spring&logoColor=C4B5FD" alt="Spring Boot 3.2.12" />
    <img src="https://img.shields.io/badge/Redis-7-12063A?style=for-the-badge&logo=redis&logoColor=C4B5FD" alt="Redis 7" />
    <img src="https://img.shields.io/badge/RabbitMQ-3.13-12063A?style=for-the-badge&logo=rabbitmq&logoColor=C4B5FD" alt="RabbitMQ" />
    <img src="https://img.shields.io/badge/Docker-OCI_Container-12063A?style=for-the-badge&logo=docker&logoColor=C4B5FD" alt="Docker OCI" />
  </p>
</div>

---

## <img src="https://img.shields.io/badge/ÍNDICE-5600EF?style=for-the-badge" height="20" align="absmiddle" /> ÍNDICE DE CONTENIDOS

1. [Nombre del servicio y su bounded context](docs/secciones/01-nombre-del-servicio-y-bounded-context.md)
2. [Arquitectura hexagonal con sus diagramas](docs/secciones/02-arquitectura-hexagonal-y-diagramas.md)
3. [Endpoints y enlace a Swagger](docs/secciones/03-endpoints-y-enlace-a-swagger.md)
4. [Eventos que publica y consume](docs/secciones/04-eventos-que-publica-y-consume.md)
5. [Tecnologías y dependencias](docs/secciones/05-tecnologias-y-dependencias.md)
6. [Cómo ejecutar local y con Docker](docs/secciones/06-como-ejecutar-local-y-docker.md)
7. [Variables de entorno](docs/secciones/07-variables-de-entorno.md)
8. [Pruebas y cobertura (≥ 85%)](docs/secciones/08-pruebas-y-cobertura.md)
9. [CI/CD con el badge del pipeline](docs/secciones/09-cicd-pipeline.md)
10. [Ramas y cómo contribuir](docs/secciones/10-ramas-y-como-contribuir.md)
11. [Seguridad y análisis OWASP Top 10](docs/secciones/11-seguridad-y-owasp.md)
12. [Equipo, responsables y enlace a la épica en Jira](docs/secciones/12-equipo-responsables-y-epica-jira.md)
