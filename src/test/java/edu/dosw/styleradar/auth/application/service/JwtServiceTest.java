package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.dosw.styleradar.auth.application.service.impl.JwtServiceImpl;
import edu.dosw.styleradar.auth.domain.model.Rol;

@DisplayName("Pruebas Unitarias de JwtService - SR-RF-02 / SR-RNF-01")
class JwtServiceTest {

    private JwtServiceImpl jwtService;

    private final String secretBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final String duration = "PT24H";

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(secretBase64, duration);
    }

    @Test
    @DisplayName("Debe generar token JWT firmado con claims de roles y subject usuarioId")
    void debeGenerarTokenConSubjectYRoles() {
        Set<Rol> roles = Set.of(Rol.COMPRADOR, Rol.ADMIN_ALMACEN);
        String token = jwtService.generarToken(123L, roles);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Debe validar exitosamente un token JWT recién generado")
    void debeValidarTokenExitosamente() {
        String token = jwtService.generarToken(99L, Set.of(Rol.COMPRADOR));

        assertThat(jwtService.validarToken(token)).isNotNull();
        assertThat(jwtService.validarToken(token).getSubject()).isEqualTo("99");
    }

    @Test
    @DisplayName("Debe rechazar tokens corruptos, nulos o con firmas incorrectas")
    void debeRechazarTokensInvalidos() {
        assertThatThrownBy(() -> jwtService.validarToken("token.invalido.falso"))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> jwtService.validarToken(""))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> jwtService.validarToken(null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Debe extraer correctamente el usuarioId desde el subject del JWT")
    void debeExtraerUsuarioId() {
        String token = jwtService.generarToken(456L, Set.of(Rol.ADMIN_STYLERADAR));

        Long usuarioId = jwtService.obtenerUsuarioId(token);

        assertThat(usuarioId).isEqualTo(456L);
    }

    @Test
    @DisplayName("Debe extraer los roles asignados desde los claims del JWT")
    void debeExtraerRoles() {
        String token = jwtService.generarToken(789L, Set.of(Rol.REPRESENTANTE_FUNDACION));

        Set<Rol> roles = jwtService.obtenerRoles(token);

        assertThat(roles).containsExactly(Rol.REPRESENTANTE_FUNDACION);
    }

    @Test
    @DisplayName("Debe calcular el tiempo restante en segundos de un token válido")
    void debeCalcularTiempoRestanteSegundos() {
        String token = jwtService.generarToken(100L, Set.of(Rol.COMPRADOR));

        long tiempoRestante = jwtService.obtenerTiempoRestanteSegundos(token);

        assertThat(tiempoRestante).isGreaterThan(86300L).isLessThanOrEqualTo(86400L);
    }
}

