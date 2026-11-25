[⬅ Volver al README Principal](../../README.md)

---

## 9. CI/CD CON EL BADGE DEL PIPELINE

[![CI](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci.yml/badge.svg)](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci.yml)
[![CI/CD QA](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci-qa.yml/badge.svg?branch=develop)](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci-qa.yml)
[![CD PROD](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci-prod.yml/badge.svg?branch=main)](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci-prod.yml)

Tres workflows, igual que el repo base:

| Workflow | Disparador | Etapas | Destino |
| :--- | :--- | :--- | :--- |
| [`ci.yml`](../../.github/workflows/ci.yml) | PR a `develop` y `main`; push a `feature/**`, `fix/**`, `docs/**` | `./mvnw verify` (JaCoCo ≥ 85 % líneas / ≥ 80 % ramas) → SpotBugs → SonarCloud → build de la imagen sin publicar | Solo revisión, no despliega |
| [`ci-qa.yml`](../../.github/workflows/ci-qa.yml) | Push a `develop` o manual | Pruebas → SpotBugs → SonarCloud → imagen `styleradar-auth:<sha>` (+ `qa-latest`) → Azure QA → smoke test | `styleradar-auth-qa` |
| [`ci-prod.yml`](../../.github/workflows/ci-prod.yml) | Push a `main` o manual (solo despliega desde `main`) | Pruebas → SpotBugs → SonarCloud → imagen `styleradar-auth:<sha>` (+ `prod-latest`) → Azure PROD → smoke test | `styleradar-auth-prod` |

```mermaid
flowchart LR
    PR["PR / feature/*"] --> CI["ci.yml<br>pruebas · JaCoCo · SpotBugs · Sonar · docker build"]
    DEV["push develop"] --> QA["ci-qa.yml"] --> E["Azure App Service QA<br>styleradar-auth-qa"] --> G["Smoke test: OpenAPI · Swagger · Health"]
    MAIN["push main"] --> PRD["ci-prod.yml"] --> F["Azure App Service PROD<br>styleradar-auth-prod"] --> H["Smoke test: Health"]
```

* El JAR se compila y prueba una sola vez por ejecución; la imagen reutiliza ese JAR validado (`actions/upload-artifact`).
* Los despliegues usan la etiqueta inmutable del commit (`<sha>`), no `latest`.
* **Quality Gate:** confiabilidad, seguridad y mantenibilidad en **A**, 100 % de *hotspots* revisados y cobertura del código nuevo ≥ 80 %. Si no se cumple, el pipeline se detiene antes de construir la imagen.

<!-- evidencias -->
### Evidencia: pipelines y calidad

<div align="center">
  <img src="../evidencias/pipeline-qa.png" alt="Pipeline QA" width="90%" />
  <p><sub><b>Figura 9.1:</b> ejecuciones de `ci-qa.yml` hacia QA (rama `develop`).</sub></p>
</div>

<div align="center">
  <img src="../evidencias/pipeline-prod.png" alt="Pipeline PROD" width="90%" />
  <p><sub><b>Figura 9.2:</b> ejecuciones de `ci-prod.yml` hacia PROD (rama `main`).</sub></p>
</div>

<div align="center">
  <img src="../evidencias/sonarqube-dashboard.png" alt="SonarCloud" width="90%" />
  <p><sub><b>Figura 9.3:</b> SonarCloud sobre `main`: Quality Gate *Passed*, cobertura 89.3 % y 0 issues abiertos.</sub></p>
</div>

<!-- /evidencias -->

---

[⬅ Volver al README Principal](../../README.md)
