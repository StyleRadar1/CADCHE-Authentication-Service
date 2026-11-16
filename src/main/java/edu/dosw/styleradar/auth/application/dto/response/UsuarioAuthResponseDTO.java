package edu.dosw.styleradar.auth.application.dto.response;

import java.time.Instant;
import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos de la cuenta creada durante el registro de comprador en ms-auth")
public class UsuarioAuthResponseDTO {

    @Schema(description = "Identificador único del usuario registrado", example = "12")
    private Long id;

    @Schema(description = "Nombre completo del usuario", example = "Laura Gómez")
    private String nombre;

    @Schema(description = "Correo electrónico registrado", example = "laura.gomez@styleradar.com")
    private String email;

    @Schema(description = "Teléfono de contacto registrado", example = "+57 300 123 4567")
    private String telefono;

    @Schema(description = "Fecha y hora UTC de creación de la cuenta", example = "2026-11-03T01:30:00Z")
    private Instant fechaRegistro;

    @Schema(description = "Roles asignados inicialmente al usuario", example = "[\"COMPRADOR\"]")
    private Set<Rol> roles;
}

