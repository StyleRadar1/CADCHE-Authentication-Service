package edu.dosw.styleradar.auth.domain.model;

/**
 * Estado del código OTP recién enviado al correo (SR-RF-92, pedir otro código).
 */
public record DesafioOtp(
        String email,
        long expiraEnSegundos,
        int intentosRestantes,
        int reenviosRestantes) {
}
