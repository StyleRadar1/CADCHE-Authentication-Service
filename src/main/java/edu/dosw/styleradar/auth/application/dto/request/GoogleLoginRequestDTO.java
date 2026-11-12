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
@Schema(description = "Solicitud de inicio de sesión mediante ID Token OpenID Connect emitido por Google")
public class GoogleLoginRequestDTO {

    @NotBlank(message = "El ID Token de Google es obligatorio")
    @Size(max = 8192, message = "El ID Token de Google excede la longitud máxima permitida")
    @Schema(
            description = "JWT ID Token firmado con RS256 obtenido tras autenticar con Google Identity Services",
            example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDk4NzY1NDMyMSIsImVtYWlsIjoidXN1YXJpb0BnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZX0.firma",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String idToken;
}

