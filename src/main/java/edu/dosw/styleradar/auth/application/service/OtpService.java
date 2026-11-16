package edu.dosw.styleradar.auth.application.service;

public interface OtpService {

    String generarOtp(String email, Long usuarioId, String rol);

    default String generarOtp(String email) {
        return generarOtp(email, null, null);
    }

    void verificarOtp(String email, String codigo);

    String reenviarOtp(String email);

    Long obtenerUsuarioId(String email);

    String obtenerRol(String email);

    int obtenerIntentosRestantes(String email);

    long obtenerSegundosRestantesBloqueo(String email);

    void eliminarOtp(String email);

    void invalidarToken(String token, long ttlSegundos);

    boolean esTokenInvalido(String token);

    boolean estaUsuarioRevocado(Long usuarioId, long fechaEmisionMillis);
}
