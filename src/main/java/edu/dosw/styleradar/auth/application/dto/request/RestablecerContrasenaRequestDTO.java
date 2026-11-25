package edu.dosw.styleradar.auth.application.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ResetPasswordRequest", description = "Reset a forgotten password with the 6-digit code sent by email")
public class RestablecerContrasenaRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 254, message = "El correo electrónico excede la longitud máxima permitida")
    @Schema(description = "Email of the account", example = "buyer@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonDeserialize(using = RecortarEspaciosDeserializer.class)
    private String email;

    @NotBlank(message = "El código OTP es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El código OTP debe contener exactamente 6 dígitos numéricos")
    @Schema(description = "6-digit code sent by email, valid for 5 minutes, 3 attempts", example = "482915", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otpCode;

    @NotBlank(message = "La contraseña nueva es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,72}$",
            message = "La contraseña debe incluir al menos una mayúscula, una minúscula, un número y un carácter especial (el espacio no cuenta)"
    )
    @Schema(description = "New password: 8 to 72 characters with upper case, lower case, digit and symbol. "
            + "Sent to ms-usuarios, which stores it with BCrypt; never stored by ms-auth",
            example = "NuevaClave2026*", requiredMode = Schema.RequiredMode.REQUIRED)
    @ToString.Exclude
    private String newPassword;
}
