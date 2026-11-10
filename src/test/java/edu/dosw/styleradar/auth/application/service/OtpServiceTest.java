package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de OtpService - SR-RF-92")
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
    }

    @Test
    @DisplayName("Debe generar OTP de 6 dígitos y guardar 4 claves en Redis")
    void debeGenerarOtpDe6DigitosYGuardarEnRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String otp = otpService.generarOtp(email, usuarioId, rol);

        assertThat(otp).isNotNull().matches("^\\d{6}$");

        verify(valueOperations).set(eq("otp:" + email), eq(otp), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:intentos:" + email), eq("3"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:usuario:" + email), eq("42"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:rol:" + email), eq("COMPRADOR"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Debe verificar exitosamente cuando el código coincide")
    void debeVerificarOtpExitosamente() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn("123456");

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
        when(valueOperations.get("otp:" + email)).thenReturn("999999");
        when(valueOperations.decrement("otp:intentos:" + email)).thenReturn(2L);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpInvalidoException.class)
                .hasMessageContaining("2");

        verify(valueOperations).decrement("otp:intentos:" + email);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Debe eliminar las claves cuando se agotan los intentos")
    void debeLanzarOtpIntentosAgotadosExceptionAlTercerFallo() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn("999999");
        when(valueOperations.decrement("otp:intentos:" + email)).thenReturn(0L);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpIntentosAgotadosException.class)
                .hasMessageContaining("agotaron los intentos");

        verify(redisTemplate).delete("otp:" + email);
        verify(redisTemplate).delete("otp:intentos:" + email);
        verify(redisTemplate).delete("otp:usuario:" + email);
        verify(redisTemplate).delete("otp:rol:" + email);
    }

    @Test
    @DisplayName("Debe agotar los intentos cuando Redis devuelve null al decrementar")
    void debeAgotarIntentosCuandoDecrementoDevuelveNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn("999999");
        when(valueOperations.decrement("otp:intentos:" + email)).thenReturn(null);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpIntentosAgotadosException.class);

        verify(redisTemplate).delete("otp:" + email);
    }

    @Test
    @DisplayName("Debe reenviar OTP cuando existe una sesión activa")
    void debeReenviarOtpSiSesionActivaExiste() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn("42");
        when(valueOperations.get("otp:rol:" + email)).thenReturn("COMPRADOR");

        String nuevoCodigo = otpService.reenviarOtp(email);

        assertThat(nuevoCodigo).isNotNull().matches("^\\d{6}$");

        verify(valueOperations).set(eq("otp:" + email), eq(nuevoCodigo), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:intentos:" + email), eq("3"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:usuario:" + email), eq("42"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:rol:" + email), eq("COMPRADOR"), eq(300L), eq(TimeUnit.SECONDS));
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
    @DisplayName("Debe obtener el ID del usuario desde Redis")
    void debeObtenerUsuarioId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn("42");

        assertThat(otpService.obtenerUsuarioId(email)).isEqualTo(42L);
    }

    @Test
    @DisplayName("Debe retornar null cuando no existe el ID del usuario")
    void debeRetornarNullCuandoNoExisteUsuarioId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn(null);

        assertThat(otpService.obtenerUsuarioId(email)).isNull();
    }

    @Test
    @DisplayName("Debe obtener el rol del usuario desde Redis")
    void debeObtenerRol() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:rol:" + email)).thenReturn("COMPRADOR");

        assertThat(otpService.obtenerRol(email)).isEqualTo("COMPRADOR");
    }

    @Test
    @DisplayName("Debe retornar null cuando no existe el rol")
    void debeRetornarNullCuandoNoExisteRol() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:rol:" + email)).thenReturn(null);

        assertThat(otpService.obtenerRol(email)).isNull();
    }

    @Test
    @DisplayName("Debe obtener los intentos restantes desde Redis")
    void debeObtenerIntentosRestantes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:intentos:" + email)).thenReturn("2");

        assertThat(otpService.obtenerIntentosRestantes(email)).isEqualTo(2);
    }

    @Test
    @DisplayName("Debe retornar cero cuando no existen intentos registrados")
    void debeRetornarCeroCuandoNoHayIntentos() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:intentos:" + email)).thenReturn(null);

        assertThat(otpService.obtenerIntentosRestantes(email)).isZero();
    }

    @Test
    @DisplayName("Debe eliminar las cuatro claves OTP de Redis")
    void debeEliminarOtp() {
        otpService.eliminarOtp(email);

        verify(redisTemplate).delete("otp:" + email);
        verify(redisTemplate).delete("otp:intentos:" + email);
        verify(redisTemplate).delete("otp:usuario:" + email);
        verify(redisTemplate).delete("otp:rol:" + email);
    }

    @Test
    @DisplayName("Debe guardar un token revocado en Redis")
    void debeInvalidarTokenEnBlacklist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        otpService.invalidarToken("token_jwt_ejemplo", 3600L);

        verify(valueOperations).set(
                eq("blacklist:token_jwt_ejemplo"),
                eq("revoked"),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("No debe guardar un token cuando es null o el TTL no es positivo")
    void noDebeInvalidarTokenConParametrosInvalidos() {
        otpService.invalidarToken(null, 3600L);
        otpService.invalidarToken("token", 0L);
        otpService.invalidarToken("token", -1L);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("Debe comprobar si un token está en la lista negra")
    void debeComprobarSiTokenEstaEnListaNegra() {
        when(redisTemplate.hasKey("blacklist:token_revocado")).thenReturn(true);
        when(redisTemplate.hasKey("blacklist:token_valido")).thenReturn(false);

        assertThat(otpService.esTokenInvalido("token_revocado")).isTrue();
        assertThat(otpService.esTokenInvalido("token_valido")).isFalse();
        assertThat(otpService.esTokenInvalido(null)).isTrue();
    }

    @Test
    @DisplayName("Debe utilizar memoria local cuando Redis no está disponible")
    void debeUsarMemoriaLocalCuandoRedisNoEstaDisponible() {
        when(redisTemplate.opsForValue())
                .thenThrow(new DataAccessResourceFailureException("Redis no disponible"));

        String otp = otpService.generarOtp(email, usuarioId, rol);

        assertThat(otp).isNotNull().matches("^\\d{6}$");
        assertThat(otpService.obtenerUsuarioId(email)).isEqualTo(usuarioId);
        assertThat(otpService.obtenerRol(email)).isEqualTo(rol);
        assertThat(otpService.obtenerIntentosRestantes(email)).isEqualTo(3);
    }

    @Test
    @DisplayName("Debe utilizar memoria local para decrementar intentos cuando Redis falla")
    void debeDecrementarIntentosEnMemoriaLocal() {
        when(redisTemplate.opsForValue())
                .thenThrow(new DataAccessResourceFailureException("Redis no disponible"));

        otpService.generarOtp(email, usuarioId, rol);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "000000"))
                .isInstanceOf(OtpInvalidoException.class)
                .hasMessageContaining("2");

        assertThat(otpService.obtenerIntentosRestantes(email)).isEqualTo(2);
    }

    @Test
    @DisplayName("Debe eliminar las claves de memoria local cuando Redis falla")
    void debeEliminarClavesLocalesCuandoRedisFalla() {
        when(redisTemplate.opsForValue())
                .thenThrow(new DataAccessResourceFailureException("Redis no disponible"));

        doThrow(new DataAccessResourceFailureException("Redis no disponible"))
                .when(redisTemplate).delete(anyString());

        otpService.generarOtp(email, usuarioId, rol);
        otpService.eliminarOtp(email);

        assertThat(otpService.obtenerUsuarioId(email)).isNull();
        assertThat(otpService.obtenerRol(email)).isNull();
        assertThat(otpService.obtenerIntentosRestantes(email)).isZero();
    }

    @Test
    @DisplayName("Debe comprobar la lista negra usando memoria local cuando Redis falla")
    void debeConsultarBlacklistEnMemoriaLocalCuandoRedisFalla() {
        when(redisTemplate.opsForValue())
                .thenThrow(new DataAccessResourceFailureException("Redis no disponible"));

        when(redisTemplate.hasKey("blacklist:token_local"))
                .thenThrow(new DataAccessResourceFailureException("Redis no disponible"));

        otpService.invalidarToken("token_local", 3600L);

        assertThat(otpService.esTokenInvalido("token_local")).isTrue();
        assertThat(otpService.esTokenInvalido("otro_token")).isFalse();
    }

    @Test
    @DisplayName("Debe retornar falso cuando Redis no encuentra un token en la lista negra")
    void debeRetornarFalseCuandoRedisNoEncuentraToken() {
        when(redisTemplate.hasKey("blacklist:token_valido")).thenReturn(null);

        assertThat(otpService.esTokenInvalido("token_valido")).isFalse();
    }
}

