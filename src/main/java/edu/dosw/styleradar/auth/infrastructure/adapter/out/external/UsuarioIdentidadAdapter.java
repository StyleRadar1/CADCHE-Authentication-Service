package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;
import edu.dosw.styleradar.auth.domain.model.IdentidadUsuario;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Adaptador de salida (anti-corrupción) que implementa {@link UsuarioIdentidadPort} sobre el cliente HTTP
 * {@link UsuarioClient} y traduce el contrato interno de ms-usuarios al modelo del dominio.
 */
@Component
public class UsuarioIdentidadAdapter implements UsuarioIdentidadPort {

    private final UsuarioClient usuarioClient;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Cliente HTTP administrado por Spring e inyectado por constructor.")
    public UsuarioIdentidadAdapter(UsuarioClient usuarioClient) {
        this.usuarioClient = usuarioClient;
    }

    @Override
    public IdentidadUsuario verificarCredenciales(String email, String password) {
        return aDominio(usuarioClient.verificarCredenciales(email, password));
    }

    @Override
    public IdentidadUsuario resolverUsuarioOAuth(String email, String nombre, String proveedor, String proveedorId) {
        return aDominio(usuarioClient.resolverUsuarioOAuth(email, nombre, proveedor, proveedorId));
    }

    @Override
    public void marcarCorreoVerificado(Long usuarioId) {
        usuarioClient.marcarCorreoVerificado(usuarioId);
    }

    private static IdentidadUsuario aDominio(VerificarCredencialesResponseDTO dto) {
        if (dto == null || dto.id() == null) {
            throw new ServicioExternoNoDisponibleException(
                    "Respuesta inválida del servicio de usuarios al verificar la identidad");
        }
        return new IdentidadUsuario(
                dto.id(),
                dto.email(),
                Rol.desde(dto.rol()),
                aEstado(dto.estado()),
                Boolean.TRUE.equals(dto.correoVerificado()));
    }

    private static EstadoCuenta aEstado(String estado) {
        try {
            return EstadoCuenta.fromValue(estado);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
