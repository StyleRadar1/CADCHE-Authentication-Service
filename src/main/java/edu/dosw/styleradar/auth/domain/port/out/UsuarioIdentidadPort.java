package edu.dosw.styleradar.auth.domain.port.out;

import edu.dosw.styleradar.auth.domain.model.IdentidadUsuario;

/**
 * Puerto de salida hacia ms-usuarios (puerto 8120), dueño de las cuentas y contraseñas.
 */
public interface UsuarioIdentidadPort {

    /**
     * Valida correo y contraseña. Lanza {@code CredencialesIncorrectasException} (401),
     * {@code CuentaNoActivaException} (403) o {@code ServicioExternoNoDisponibleException} (503).
     */
    IdentidadUsuario verificarCredenciales(String email, String password);

    /**
     * Crea o vincula la cuenta asociada a un proveedor OAuth2 (Google).
     */
    IdentidadUsuario resolverUsuarioOAuth(String email, String nombre, String proveedor, String proveedorId);

    /**
     * Marca el correo de la cuenta como verificado de forma permanente (contrato U3).
     */
    void marcarCorreoVerificado(Long usuarioId);
}
