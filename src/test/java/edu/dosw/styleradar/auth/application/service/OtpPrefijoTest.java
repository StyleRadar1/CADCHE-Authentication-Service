package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import edu.dosw.styleradar.auth.application.service.impl.OtpServiceImpl;

@DisplayName("OtpServiceImpl: prefijo de claves por propósito y límite de solicitudes")
class OtpPrefijoTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "reset"})
    void prefijoInvalidoSeRechaza(String prefijo) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        assertThatThrownBy(() -> new OtpServiceImpl(redis, false, null, prefijo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void codigosYSolicitudesUsanElPrefijoPropio() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.increment("reset:solicitudes:buyer@styleradar.com")).thenReturn(1L, 4L, 5L);
        OtpServiceImpl reset = new OtpServiceImpl(redis, false, null, "reset:");

        reset.generarOtp("buyer@styleradar.com", 12L, "BUYER");
        verify(valores).set(eq("reset:buyer@styleradar.com"), anyString(), eq(300L),
                eq(java.util.concurrent.TimeUnit.SECONDS));

        assertThat(reset.registrarSolicitud("buyer@styleradar.com", 4, 900)).isTrue();
        assertThat(reset.registrarSolicitud("buyer@styleradar.com", 4, 900)).isTrue();
        assertThat(reset.registrarSolicitud("buyer@styleradar.com", 4, 900)).isFalse();
    }
}
