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
@Schema(name = "RegisteredUserResponse", description = "Support account returned by the ms-auth registration endpoint (annex, disabled by default)")
public class UsuarioAuthResponseDTO {

    @Schema(description = "Account identifier", example = "12")
    private Long id;

    @Schema(description = "Full name", example = "Laura Gómez")
    private String name;

    @Schema(description = "Registered email", example = "laura.gomez@styleradar.com")
    private String email;

    @Schema(description = "Contact phone", example = "+57 300 123 4567")
    private String phone;

    @Schema(description = "UTC date and time the account was created", example = "2026-11-03T01:30:00Z")
    private Instant registeredAt;

    @Schema(description = "Initial roles of the account", example = "[\"BUYER\"]")
    private Set<Rol> roles;
}
