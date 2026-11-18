package edu.dosw.styleradar.auth.application.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
@Schema(description = "Login credentials (SR-RF-02)")
public class LoginRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 254, message = "El correo electrónico excede la longitud máxima permitida")
    @Schema(description = "Registered email (max 254 characters, case insensitive)", example = "buyer@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonDeserialize(using = RecortarEspaciosDeserializer.class)
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 30, message = "La contraseña debe tener entre 8 y 30 caracteres")
    @Schema(description = "Account password (8 to 30 characters), sent over TLS and never stored", example = "StyleRadar2026*", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
