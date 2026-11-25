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
@Schema(name = "GoogleLoginRequest", description = "Login request with a Google OpenID Connect ID token")
public class GoogleLoginRequestDTO {

    @NotBlank(message = "El ID Token de Google es obligatorio")
    @Size(max = 8192, message = "El ID Token de Google excede la longitud máxima permitida")
    @Schema(
            description = "RS256-signed ID token obtained from Google Identity Services",
            example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDk4NzY1NDMyMSIsImVtYWlsIjoidXN1YXJpb0BnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZX0.firma",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String idToken;
}

