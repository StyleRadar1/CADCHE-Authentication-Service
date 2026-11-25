[⬅ Volver al README Principal](../../README.md)

---

## 9. CI/CD CON EL BADGE DEL PIPELINE

[![CI/CD](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/StyleRadar1/CADCHE-Authentication-Service/actions/workflows/ci.yml)

Un solo workflow, [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml):

```mermaid
flowchart LR
    A["Build + pruebas<br>JaCoCo ≥ 85 %"] --> B["SpotBugs"] --> C["SonarCloud<br>Quality Gate (wait)"] --> D["Imagen OCI<br>Docker Hub · tag = SHA"]
    D -->|develop · feature/* · fix/*| E["Azure App Service QA<br>styleradar-auth-qa"]
    D -->|main| F["Azure App Service PROD<br>styleradar-auth-prod"]
    E --> G["Smoke test: OpenAPI · Swagger · Health"]
    F --> H["Smoke test: Health"]
```

| Ambiente | Rama | App Service | Imagen |
| :--- | :--- | :--- | :--- |
| QA | `develop`, `feature/*`, `fix/*` | `styleradar-auth-qa` | `styleradar-auth:<sha>` (+ `qa-latest`) |
| PROD | `main` | `styleradar-auth-prod` | `styleradar-auth:<sha>` (+ `prod-latest`) |

* El JAR se compila y prueba una sola vez; la imagen reutiliza ese JAR validado (`actions/upload-artifact`).
* Los despliegues usan la etiqueta inmutable del commit (`<sha>`), no `latest`.
* **Quality Gate:** confiabilidad, seguridad y mantenibilidad en **A**, 100 % de *hotspots* revisados y cobertura del código nuevo ≥ 80 %. Si no se cumple, el pipeline se detiene antes de construir la imagen.

<!-- evidencias -->
### Evidencia: pipelines y calidad

<div align="center">
  <img src="../evidencias/pipeline-qa.png" alt="Pipeline QA" width="90%" />
  <p><sub><b>Figura 9.1:</b> ejecuciones de `ci.yml` hacia QA (rama `develop`).</sub></p>
</div>

<div align="center">
  <img src="../evidencias/pipeline-prod.png" alt="Pipeline PROD" width="90%" />
  <p><sub><b>Figura 9.2:</b> ejecuciones de `ci.yml` hacia PROD (rama `main`).</sub></p>
</div>

<div align="center">
  <img src="../evidencias/sonarqube-dashboard.png" alt="SonarCloud" width="90%" />
  <p><sub><b>Figura 9.3:</b> SonarCloud sobre `main`: Quality Gate *Passed*, cobertura 89.3 % y 0 issues abiertos.</sub></p>
</div>

<!-- /evidencias -->

---

[⬅ Volver al README Principal](../../README.md)
