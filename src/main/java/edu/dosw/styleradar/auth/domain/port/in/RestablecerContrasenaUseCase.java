package edu.dosw.styleradar.auth.domain.port.in;

import edu.dosw.styleradar.auth.domain.model.SolicitudRestablecimiento;

/**
 * Restablecimiento de contraseña olvidada con código OTP enviado al correo. Aplica a todas las cuentas
 * (comprador, administrador de almacén, representante de fundación y administrador StyleRadar), porque
 * todas viven en ms-usuarios.
 */
public interface RestablecerContrasenaUseCase {

    /**
     * Envía un código de 6 dígitos si el correo pertenece a una cuenta activa. Responde igual si no existe.
     */
    SolicitudRestablecimiento solicitar(String email);

    /**
     * Valida el código y reemplaza la contraseña en ms-usuarios. Después cierra todas las sesiones de la
     * cuenta; el usuario debe iniciar sesión de nuevo con la contraseña nueva.
     */
    void restablecer(String email, String codigo, String nuevaContrasena);
}
