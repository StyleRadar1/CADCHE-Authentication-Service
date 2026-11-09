package edu.dosw.styleradar.auth.application.service;

public interface OtpService {

    String generarOtp(String email, Long usuarioId, String rol);

    void verificarOtp(String email, String codigo);

    Long obtenerUsuarioId(String email);

    String obtenerRol(String email);

    int obtenerIntentosRestantes(String email);

    void eliminarOtp(String email);
}