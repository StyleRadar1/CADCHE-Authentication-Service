package edu.dosw.styleradar.auth.domain.port.out;

import java.util.Optional;

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

    /**
     * Busca la cuenta por correo para el restablecimiento de contraseña. Devuelve vacío si no existe
     * (contrato U6: {@code POST /api/v1/internal/users/lookup}).
     */
    Optional<IdentidadUsuario> buscarPorCorreo(String email);

    /**
     * Reemplaza la contraseña de la cuenta. ms-usuarios la guarda con BCrypt; ms-auth nunca la almacena
     * (contrato U7: {@code PUT /api/v1/internal/users/{id}/password}).
     */
    void cambiarContrasena(Long usuarioId, String nuevaContrasena);
}
