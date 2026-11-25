[⬅ Volver al README Principal](../../README.md)

---

## 10. RAMAS Y CÓMO CONTRIBUIR

```text
main        <-- Producción (despliega PROD)
└── develop <-- Integración (despliega QA)
    └── feature/<tarea>   <-- Todo cambio nace aquí y entra a develop por Pull Request
```

* Nada de commits directos a `develop` ni a `main`: todo va por `feature/*` y PR a `develop`.
* Mensajes en minúsculas y en imperativo: `tipo(alcance): descripción`. Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`. Ejemplo: `fix(auth): respetar espera de 60s en login`.

### Checklist para Pull Requests

- [ ] La rama parte del `develop` más reciente.
- [ ] `mvn -B clean verify` pasa (pruebas y JaCoCo ≥ 85 %).
- [ ] SpotBugs sin hallazgos y Sonar en A.
- [ ] Swagger actualizado si cambió un contrato (y aviso en Slack a los equipos que lo consumen).
- [ ] Sin credenciales ni secretos en el código.
- [ ] Al menos 1 revisor del equipo.

---

[⬅ Volver al README Principal](../../README.md)
