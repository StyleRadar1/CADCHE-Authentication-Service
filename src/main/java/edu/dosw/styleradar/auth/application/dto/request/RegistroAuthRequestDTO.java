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
@Schema(name = "RegisterRequest", description = "Support registration in ms-auth (annex). Disabled by default; the real buyer registration is POST /api/v1/users in ms-usuarios")
public class RegistroAuthRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}0-9 .,'\\-]+$",
            message = "El nombre contiene caracteres no permitidos"
    )
    @Schema(description = "Buyer full name (2 to 100 characters)", example = "Laura Gómez", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 254, message = "El correo electrónico excede la longitud máxima permitida")
    @Schema(description = "Unique login email", example = "laura.gomez@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Pattern(regexp = "^[+0-9\\-\\s()]{7,20}$", message = "El formato del teléfono es inválido")
    @Schema(description = "Optional contact phone (7 to 20 characters)", example = "+57 300 123 4567")
    private String phone;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 30, message = "La contraseña debe tener entre 8 y 30 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,30}$",
            message = "La contraseña debe incluir al menos una mayúscula, una minúscula, un número y un carácter especial"
    )
    @Schema(description = "Password of 8 to 30 characters with upper case, lower case, digit and symbol. Validated, never stored", example = "StyleRadar2026*", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Builder.Default
    @AssertTrue(message = "Debe aceptar el tratamiento de datos personales (Habeas Data)")
    @Schema(description = "Explicit consent to personal data processing (Law 1581). Must be true", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean dataPolicyAccepted = true;
}
