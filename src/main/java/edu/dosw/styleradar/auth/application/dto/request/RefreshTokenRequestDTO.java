package edu.dosw.styleradar.auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RefreshTokenRequest", description = "Request to refresh the JWT of an active session")
public class RefreshTokenRequestDTO {

    @NotBlank(message = "El token de renovación es obligatorio")
    @Size(max = 2048, message = "El token excede la longitud máxima permitida")
    @Schema(description = "Valid JWT previously issued by ms-auth (max 2048 characters)", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsImlhdCI6MTczMDY1OTIwMCwiZXhwIjoxNzMwNzQ1NjAwfQ.firma", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
