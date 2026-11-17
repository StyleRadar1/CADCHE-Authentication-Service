package edu.dosw.styleradar.auth.domain.model;

/**
 * Resultado de un inicio de sesión. Si el correo no está verificado no hay token y se exige el
 * código OTP (SR-RF-02 FA-13 / SR-RF-92).
 */
public record SesionAutenticada(
        String accessToken,
        long expiraEnSegundos,
        Long usuarioId,
        Rol rol,
        String email,
        boolean correoVerificado,
        boolean requiereOtp,
        Long otpExpiraEnSegundos,
        int intentosOtpRestantes) {

    public static SesionAutenticada conToken(
            String accessToken, long expiraEnSegundos, Long usuarioId, Rol rol, String email) {
        return new SesionAutenticada(accessToken, expiraEnSegundos, usuarioId, rol, email, true, false, null, 0);
    }

    public static SesionAutenticada pendienteDeVerificacion(
            long expiraEnSegundos, Long usuarioId, Rol rol, String email, long otpExpiraEnSegundos, int intentos) {
        return new SesionAutenticada(null, expiraEnSegundos, usuarioId, rol, email, false, true,
                otpExpiraEnSegundos, intentos);
    }
}
