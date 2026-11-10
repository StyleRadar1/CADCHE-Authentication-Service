package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @DisplayName("Debe generar OTP de exactamente 6 dígitos numéricos y persistir 4 claves en Redis con TTL de 300s")
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
    @DisplayName("Debe verificar exitosamente cuando el código coincide con el almacenado en Redis")
    void debeVerificarOtpExitosamente() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn("123456");

        otpService.verificarOtp(email, "123456");

        verify(valueOperations, never()).decrement(anyString());
    }

    @Test
    @DisplayName("Debe lanzar OtpExpiradoException cuando el código no existe en Redis (expirado)")
    void debeLanzarOtpExpiradoExceptionCuandoNoExiste() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:" + email)).thenReturn(null);

        assertThatThrownBy(() -> otpService.verificarOtp(email, "123456"))
                .isInstanceOf(OtpExpiradoException.class)
                .hasMessageContaining("expiró");
    }

    @Test
    @DisplayName("Debe decrementar intentos y lanzar OtpInvalidoException cuando el código es incorrecto y quedan intentos")
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
    @DisplayName("Debe bloquear y eliminar claves en Redis cuando se agotan los 3 intentos fallidos (HTTP 429)")
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
    @DisplayName("Debe regenerar y reenviar nuevo código OTP de 6 dígitos cuando hay sesión activa en Redis")
    void debeReenviarOtpSiSesionActivaExiste() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn("42");
        when(valueOperations.get("otp:rol:" + email)).thenReturn("COMPRADOR");

        String nuevoCodigo = otpService.reenviarOtp(email);

        assertThat(nuevoCodigo).isNotNull().matches("^\\d{6}$");
        verify(valueOperations).set(eq("otp:" + email), eq(nuevoCodigo), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("otp:intentos:" + email), eq("3"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Debe lanzar OtpExpiradoException al intentar reenviar si no hay sesión previa en Redis")
    void debeLanzarExcepcionAlReenviarSiNoHaySesion() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:usuario:" + email)).thenReturn(null);

        assertThatThrownBy(() -> otpService.reenviarOtp(email))
                .isInstanceOf(OtpExpiradoException.class)
                .hasMessageContaining("No se encontró una sesión activa");
    }

    @Test
    @DisplayName("Debe invalidar token JWT guardándolo en la lista negra de Redis con su TTL restante")
    void debeInvalidarTokenEnBlacklist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        otpService.invalidarToken("token_jwt_ejemplo", 3600L);

        verify(valueOperations).set(eq("blacklist:token_jwt_ejemplo"), eq("revoked"), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Debe comprobar correctamente si un token está en la lista negra")
    void debeComprobarSiTokenEstaEnListaNegra() {
        when(redisTemplate.hasKey("blacklist:token_revocado")).thenReturn(true);
        when(redisTemplate.hasKey("blacklist:token_valido")).thenReturn(false);

        assertThat(otpService.esTokenInvalido("token_revocado")).isTrue();
        assertThat(otpService.esTokenInvalido("token_valido")).isFalse();
        assertThat(otpService.esTokenInvalido(null)).isTrue();
    }
}

