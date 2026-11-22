package edu.dosw.styleradar.auth.application.dto.response;

import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Session response of SR-RF-02 and SR-RF-92 (requirements analysis v2, 17/11/2026).
 */
@Schema(description = "Authentication result: JWT Bearer token, or OTP challenge when the email is not verified yet")
public record LoginResponseDTO(
        @Schema(description = "Signed JWT (HS256). Null while the email still has to be verified with the OTP code", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkJVWUVSIl19.signature", nullable = true)
        String accessToken,

        @Schema(description = "HTTP authentication scheme", example = "Bearer")
        String tokenType,

        @Schema(description = "Session lifetime in seconds, taken from JWT_DURATION (24 h dev, 12 h QA, 8 h PROD)", example = "28800")
        long expiresIn,

        @Schema(description = "Account identifier", example = "12")
        Long userId,

        @Schema(description = "Account role (exactly one): BUYER, STORE_ADMIN, FOUNDATION_REPRESENTATIVE or STYLERADAR_ADMIN", example = "[\"BUYER\"]")
        Set<Rol> roles,

        @Schema(description = "Normalized account email (lower case, no spaces)", example = "buyer@styleradar.com", nullable = true)
        String email,

        @Schema(description = "true when the email is already verified (login without code, RN-05)", example = "true")
        boolean emailVerified,

        @Schema(description = "true when the 6-digit OTP code is still required", example = "false")
        boolean otpRequired,

        @Schema(description = "OTP code lifetime in seconds (300). Null when no code was sent", example = "300", nullable = true)
        Long otpExpiresInSeconds,

        @Schema(description = "Attempts left for the OTP code (3). 0 when no code was sent", example = "0")
        int otpAttemptsRemaining,

        @Schema(description = "Result summary (Spanish text for the end user)", example = "Inicio de sesión exitoso")
        String message
) {
}
