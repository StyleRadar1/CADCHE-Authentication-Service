package edu.dosw.styleradar.auth.infrastructure.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import edu.dosw.styleradar.auth.domain.model.SesionActiva;

@DisplayName("Registro de sesiones en Redis")
class RedisRegistroSesionesAdapterTest {

    private static final Instant VENCE = Instant.parse("2026-11-26T03:04:05Z");

    private StringRedisTemplate redis;
    private HashOperations<String, Object, Object> hash;
    private ZSetOperations<String, String> zset;
    private SetOperations<String, String> set;
    private RedisRegistroSesionesAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        hash = mock(HashOperations.class);
        zset = mock(ZSetOperations.class);
        set = mock(SetOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        when(redis.opsForZSet()).thenReturn(zset);
        when(redis.opsForSet()).thenReturn(set);
        adapter = new RedisRegistroSesionesAdapter(redis);
    }

    private void registrada(String jti) {
        when(zset.remove(RedisRegistroSesionesAdapter.VENCIMIENTOS, jti)).thenReturn(1L);
        when(hash.entries("sesion:" + jti)).thenReturn(Map.of("sesionId", "sesion-1", "usuarioId", "12",
                "expira", String.valueOf(VENCE.toEpochMilli())));
    }

    @Test
    @DisplayName("Registrar guarda el hash, el vencimiento y el set del usuario con limpieza automática")
    void registrar() {
        adapter.registrar(new SesionActiva("sesion-1", 12L, "jti-1", VENCE));

        verify(hash).putAll(eq("sesion:jti-1"), anyMap());
        verify(zset).add(RedisRegistroSesionesAdapter.VENCIMIENTOS, "jti-1", VENCE.toEpochMilli());
        verify(set).add("sesiones:usuario:12", "jti-1");
        verify(redis).expireAt(eq("sesion:jti-1"), any(Date.class));
        verify(redis).expireAt(eq("sesiones:usuario:12"), any(Date.class));
    }

    @Test
    @DisplayName("Finalizar devuelve la sesión solo a quien logra quitarla del sorted set")
    void finalizar() {
        registrada("jti-1");

        SesionActiva s = adapter.finalizar("jti-1").orElseThrow();

        assertThat(s.sesionId()).isEqualTo("sesion-1");
        assertThat(s.usuarioId()).isEqualTo(12L);
        assertThat(s.expira()).isEqualTo(VENCE);
        verify(redis).delete("sesion:jti-1");
        verify(set).remove("sesiones:usuario:12", "jti-1");

        when(zset.remove(RedisRegistroSesionesAdapter.VENCIMIENTOS, "jti-x")).thenReturn(0L);
        assertThat(adapter.finalizar("jti-x")).isEmpty();
        assertThat(adapter.finalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("Sin datos del hash la sesión igual se cierra para no descuadrar el conteo")
    void finalizarSinHash() {
        when(zset.remove(RedisRegistroSesionesAdapter.VENCIMIENTOS, "jti-9")).thenReturn(1L);
        when(hash.entries("sesion:jti-9")).thenReturn(Map.of("usuarioId", "no-numero"));

        SesionActiva s = adapter.finalizar("jti-9").orElseThrow();

        assertThat(s.sesionId()).isEqualTo("jti-9");
        assertThat(s.usuarioId()).isNull();
        assertThat(s.expira()).isNull();
        verify(set, never()).remove(any(), any());
    }

    @Test
    @DisplayName("Reemplazar conserva el sessionId y registra el token renovado")
    void reemplazar() {
        registrada("jti-1");
        Instant nuevo = VENCE.plusSeconds(600);

        SesionActiva s = adapter.reemplazar("jti-1", "jti-2", nuevo).orElseThrow();

        assertThat(s.sesionId()).isEqualTo("sesion-1");
        assertThat(s.tokenId()).isEqualTo("jti-2");
        verify(zset).add(RedisRegistroSesionesAdapter.VENCIMIENTOS, "jti-2", nuevo.toEpochMilli());
        when(zset.remove(RedisRegistroSesionesAdapter.VENCIMIENTOS, "jti-viejo")).thenReturn(0L);
        assertThat(adapter.reemplazar("jti-viejo", "jti-3", nuevo)).isEmpty();
    }

    @Test
    @DisplayName("Vencidas y sesiones del usuario se cierran en lote")
    void lotes() {
        registrada("jti-1");
        Instant ahora = VENCE.plusSeconds(1);
        when(zset.rangeByScore(RedisRegistroSesionesAdapter.VENCIMIENTOS, 0, ahora.toEpochMilli(), 0, 500))
                .thenReturn(new LinkedHashSet<>(Set.of("jti-1")));
        assertThat(adapter.finalizarVencidas(ahora, 500)).hasSize(1);

        registrada("jti-2");
        when(set.members("sesiones:usuario:12")).thenReturn(Set.of("jti-2"));
        assertThat(adapter.finalizarDeUsuario(12L)).extracting(SesionActiva::tokenId).containsExactly("jti-2");
        assertThat(adapter.finalizarDeUsuario(null)).isEmpty();
        when(set.members("sesiones:usuario:99")).thenReturn(null);
        assertThat(adapter.finalizarDeUsuario(99L)).isEmpty();
    }
}
