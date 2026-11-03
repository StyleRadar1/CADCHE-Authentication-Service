package edu.dosw.styleradar.auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para registrar de forma atómica un nuevo comprador con credenciales (SR-RF-01 / SR-RF-02)")
public class RegistroAuthRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Schema(description = "Nombre completo del usuario comprador", example = "Laura Gómez", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Schema(description = "Correo electrónico único para autenticación", example = "laura.gomez@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Pattern(regexp = "^[+0-9\\-\\s()]{7,20}$", message = "El formato del teléfono es inválido")
    @Schema(description = "Número telefónico de contacto opcional", example = "+57 300 123 4567")
    private String telefono;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    @Schema(description = "Contraseña que cumple la política de seguridad de la plataforma", example = "StyleRadar2026*", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Builder.Default
    @AssertTrue(message = "Debe aceptar la política de tratamiento de datos personales (Habeas Data)")
    @Schema(description = "Consentimiento expreso de tratamiento de datos personales conforme a la Ley 1581 (SR-RNF-02)", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean aceptaHabeasData = true;
}

