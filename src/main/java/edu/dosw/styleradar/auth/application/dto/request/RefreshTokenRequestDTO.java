package edu.dosw.styleradar.auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para renovar el token de acceso JWT de una sesión activa (SR-RF-02)")
public class RefreshTokenRequestDTO {

    @NotBlank(message = "El token de renovación es obligatorio")
    @Schema(description = "Token JWT vigente o de renovación emitido previamente por ms-auth", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsImlhdCI6MTczMDY1OTIwMCwiZXhwIjoxNzMwNzQ1NjAwfQ.firma", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}

