package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import edu.dosw.styleradar.auth.application.service.impl.OtpServiceImpl;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de OtpService - SR-RF-92 / OWASP A04-A06-A07-A10:2025")
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OtpServiceImpl otpService;

    private final String email = "usuario@styleradar.com";
    private final Long usuarioId = 42L;
    private final String rol = "COMPRADOR";

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(otpService, "ttlSeconds", 300L);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 3);
        ReflectionTestUtils.setField(otpService, "maxResends", 3);
    }

    @Test
    @DisplayName("Debe generar OTP de 6 dígitos, almacenarlo con hash SHA-256 salteado y guardar claves en Redis")
    void debeGenerarOtpDe6DigitosYGuardarHashEnRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String otp = otpService.generarOtp("  Usuario@StyleRadar.COM ", usuarioId, rol);

        assertThat(otp).isNotNull().matches("^\\d{6}$");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("otp:" + email), hashCaptor.capture(), eq(300L), eq(TimeUnit.SECONDS));
        assertThat(hashCaptor.getValue())
                .isNotEqualTo(otp)
                .contains("$");

        verify(valueOperations).set(eq("otp:intentos:" + email), eq("3"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:usuario:" + email), eq("42"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:rol:" + email), eq("COMPRADOR"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Debe verificar exitosamente cuando el código coincide con el hash almacenado")
    void debeVerificarOtpExitosamente() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn(otpService.hashearOtp("123456"));

        otpService.verificarOtp(email, "123456");

        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    @DisplayName("Debe lanzar OtpExpiradoException cuando el código no existe")
    void debeLanzarOtpExpiradoExceptionCuandoNoExiste() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn(null);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "123456"))
                .isInstanceOf(OtpExpiradoException.class)
                .hasMessageContaining("expiró");
    }

    @Test
    @DisplayName("Debe decrementar intentos cuando el código es incorrecto")
    void debeDecrementarIntentosCuandoCodigoEsIncorrecto() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn(otpService.hashearOtp("999999"));
        when(valueOperations.decrement("otp:intentos:" + email)).thenReturn(2L);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpInvalidoException.class)
                .hasMessageContaining("2");

        verify(valueOperations).decrement("otp:intentos:" + email);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Debe eliminar todas las claves OTP cuando se agotan los intentos")
    void debeLanzarOtpIntentosAgotadosExceptionAlTercerFallo() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn(otpService.hashearOtp("999999"));
        when(valueOperations.decrement("otp:intentos:" + email)).thenReturn(0L);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpIntentosAgotadosException.class)
                .hasMessageContaining("agotaron los intentos");

        verify(redisTemplate).delete("otp:" + email);
        verify(redisTemplate).delete("otp:intentos:" + email);
        verify(redisTemplate).delete("otp:usuario:" + email);
        verify(redisTemplate).delete("otp:rol:" + email);
        verify(redisTemplate).delete("otp:reenvios:" + email);
    }

    @Test
    @DisplayName("Debe agotar los intentos cuando Redis devuelve null al decrementar")
    void debeAgotarIntentosCuandoDecrementoDevuelveNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn(otpService.hashearOtp("999999"));
        when(valueOperations.decrement("otp:intentos:" + email)).thenReturn(null);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpIntentosAgotadosException.class);

        verify(redisTemplate).delete("otp:" + email);
    }

    @Test
    @DisplayName("Debe reenviar OTP con hash SHA-256 cuando existe una sesión activa y reenvíos disponibles")
    void debeReenviarOtpSiSesionActivaExiste() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn("42");
        when(valueOperations.get("otp:rol:" + email)).thenReturn("COMPRADOR");
        when(valueOperations.increment("otp:reenvios:" + email)).thenReturn(1L);

        String nuevoCodigo = otpService.reenviarOtp(email);

        assertThat(nuevoCodigo).isNotNull().matches("^\\d{6}$");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("otp:" + email), hashCaptor.capture(), eq(300L), eq(TimeUnit.SECONDS));
        assertThat(hashCaptor.getValue())
                .isNotEqualTo(nuevoCodigo)
                .contains("$");

        verify(valueOperations).set(eq("otp:intentos:" + email), eq("3"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:usuario:" + email), eq("42"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:rol:" + email), eq("COMPRADOR"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Debe bloquear reenvío de OTP al superar el máximo permitido (OWASP A06:2025)")
    void debeBloquearReenvioCuandoSeAgotaLimite() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn("42");
        when(valueOperations.get("otp:rol:" + email)).thenReturn("COMPRADOR");
        when(valueOperations.increment("otp:reenvios:" + email)).thenReturn(4L);

        assertThatThrownBy(() -> otpService.reenviarOtp(email))
                .isInstanceOf(OtpIntentosAgotadosException.class)
                .hasMessageContaining("máximo de reenvíos");
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando no existe sesión para reenviar OTP")
    void debeLanzarExcepcionAlReenviarSiNoHaySesion() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn(null);
        when(valueOperations.get("otp:rol:" + email)).thenReturn("COMPRADOR");

        assertThatThrownBy(() -> otpService.reenviarOtp(email))
                .isInstanceOf(OtpExpiradoException.class)
                .hasMessageContaining("No se encontró una sesión activa");
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando falta el rol al reenviar OTP")
    void debeLanzarExcepcionCuandoNoExisteRolAlReenviar() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn("42");
        when(valueOperations.get("otp:rol:" + email)).thenReturn(null);

        assertThatThrownBy(() -> otpService.reenviarOtp(email))
                .isInstanceOf(OtpExpiradoException.class);
    }

    @Test
    @DisplayName("Debe rechazar correo con caracteres de control (OWASP A05:2025)")
    void debeRechazarCorreoConCaracteresDeControl() {
        assertThatThrownBy(() -> otpService.generarOtp("bad\r\n@styleradar.com", usuarioId, rol))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Debe obtener el ID, rol e intentos restantes desde Redis")
    void debeObtenerDatosDesdeRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn("42");
        when(valueOperations.get("otp:rol:" + email)).thenReturn("COMPRADOR");
        when(valueOperations.get("otp:intentos:" + email)).thenReturn("2");

        assertThat(otpService.obtenerUsuarioId(email)).isEqualTo(42L);
        assertThat(otpService.obtenerRol(email)).isEqualTo("COMPRADOR");
        assertThat(otpService.obtenerIntentosRestantes(email)).isEqualTo(2);
    }

    @Test
    @DisplayName("Debe retornar valores vacíos cuando las claves no existen en Redis")
    void debeRetornarValoresPorDefectoCuandoClavesNoExisten() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn(null);
        when(valueOperations.get("otp:rol:" + email)).thenReturn(null);
        when(valueOperations.get("otp:intentos:" + email)).thenReturn(null);

        assertThat(otpService.obtenerUsuarioId(email)).isNull();
        assertThat(otpService.obtenerRol(email)).isNull();
        assertThat(otpService.obtenerIntentosRestantes(email)).isZero();
    }

    @Test
    @DisplayName("Debe guardar y consultar token revocado en blacklist de Redis")
    void debeInvalidarYConsultarTokenEnBlacklist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        otpService.invalidarToken("token_jwt_ejemplo", 3600L);
        verify(valueOperations).set(
                eq("blacklist:token_jwt_ejemplo"),
                eq("revoked"),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );

        when(redisTemplate.hasKey("blacklist:token_revocado")).thenReturn(true);
        when(redisTemplate.hasKey("blacklist:token_valido")).thenReturn(false);

        assertThat(otpService.esTokenInvalido("token_revocado")).isTrue();
        assertThat(otpService.esTokenInvalido("token_valido")).isFalse();
        assertThat(otpService.esTokenInvalido(null)).isTrue();
    }

    @Test
    @DisplayName("No debe guardar token en blacklist con parámetros nulos o TTL no positivo")
    void noDebeInvalidarTokenConParametrosInvalidos() {
        otpService.invalidarToken(null, 3600L);
        otpService.invalidarToken("token", 0L);
        otpService.invalidarToken("token", -1L);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("Debe evaluar correctamente la revocación global por timestamp de usuario (OWASP A07:2025)")
    void debeValidarRevocacionGlobalDeUsuario() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("usuario:revocado:42")).thenReturn("1000");

        assertThat(otpService.estaUsuarioRevocado(42L, 900L)).isTrue();
        assertThat(otpService.estaUsuarioRevocado(42L, 1000L)).isTrue();
        assertThat(otpService.estaUsuarioRevocado(42L, 1500L)).isFalse();
        assertThat(otpService.estaUsuarioRevocado(null, 1500L)).isTrue();

        when(valueOperations.get("usuario:revocado:99")).thenReturn("corrupto");
        assertThat(otpService.estaUsuarioRevocado(99L, 1500L)).isTrue();
    }

    @Test
    @DisplayName("Debe fallar cerrado (fail-closed) fuera de perfil dev cuando Redis no está disponible (OWASP A10:2025)")
    void debeFallarCerradoCuandoRedisNoEstaDisponiblePorDefecto() {
        when(redisTemplate.opsForValue())
                .thenThrow(new DataAccessResourceFailureException("Redis caído"));
        when(redisTemplate.hasKey("blacklist:token_any"))
                .thenThrow(new DataAccessResourceFailureException("Redis caído"));

        assertThatThrownBy(() -> otpService.generarOtp(email, usuarioId, rol))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> otpService.verificarOtp(email, "123456"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> otpService.invalidarToken("token_any", 60L))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> otpService.esTokenInvalido("token_any"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
        assertThatThrownBy(() -> otpService.estaUsuarioRevocado(usuarioId, 100L))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    @DisplayName("Debe utilizar memoria local cuando fallback dev explícito está habilitado y Redis falla")
    void debeUsarMemoriaLocalCuandoFallbackDevExplicitoEstaActivo() {
        OtpServiceImpl devOtpService = new OtpServiceImpl(redisTemplate, true);
        ReflectionTestUtils.setField(devOtpService, "ttlSeconds", 300L);
        ReflectionTestUtils.setField(devOtpService, "maxAttempts", 3);
        ReflectionTestUtils.setField(devOtpService, "maxResends", 3);

        when(redisTemplate.opsForValue())
                .thenThrow(new DataAccessResourceFailureException("Redis no disponible"));
        doThrow(new DataAccessResourceFailureException("Redis no disponible"))
                .when(redisTemplate).delete(anyString());
        when(redisTemplate.hasKey("blacklist:token_local"))
                .thenThrow(new DataAccessResourceFailureException("Redis no disponible"));

        String otp = devOtpService.generarOtp(email, usuarioId, rol);

        assertThat(otp).isNotNull().matches("^\\d{6}$");
        assertThat(devOtpService.obtenerUsuarioId(email)).isEqualTo(usuarioId);
        assertThat(devOtpService.obtenerRol(email)).isEqualTo(rol);
        assertThat(devOtpService.obtenerIntentosRestantes(email)).isEqualTo(3);

        assertThatThrownBy(() -> devOtpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpInvalidoException.class)
                .hasMessageContaining("2");
        assertThat(devOtpService.obtenerIntentosRestantes(email)).isEqualTo(2);

        devOtpService.verificarOtp(email, otp);

        devOtpService.invalidarToken("token_local", 3600L);
        assertThat(devOtpService.esTokenInvalido("token_local")).isTrue();
        assertThat(devOtpService.esTokenInvalido("otro_token")).isFalse();

        devOtpService.eliminarOtp(email);
        assertThat(devOtpService.obtenerUsuarioId(email)).isNull();
        assertThat(devOtpService.obtenerRol(email)).isNull();
        assertThat(devOtpService.obtenerIntentosRestantes(email)).isZero();
    }

    @Test
    @DisplayName("Debe retornar falso cuando Redis devuelve null al consultar lista negra")
    void debeRetornarFalseCuandoRedisNoEncuentraToken() {
        when(redisTemplate.hasKey("blacklist:token_valido")).thenReturn(null);

        assertThat(otpService.esTokenInvalido("token_valido")).isFalse();
    }
}
