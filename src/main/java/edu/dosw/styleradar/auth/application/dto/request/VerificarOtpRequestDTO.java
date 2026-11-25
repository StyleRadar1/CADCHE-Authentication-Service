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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerifyOtpRequest", description = "Verification of the 6-digit OTP code sent by email")
public class VerificarOtpRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 254, message = "El correo electrónico excede la longitud máxima permitida")
    @Schema(description = "Email of the ongoing OTP verification", example = "buyer@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonDeserialize(using = RecortarEspaciosDeserializer.class)
    private String email;

    @NotBlank(message = "El código OTP es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El código OTP debe contener exactamente 6 dígitos numéricos")
    @Schema(description = "Single-use 6-digit numeric code, valid for 5 minutes, 3 attempts", example = "482915", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otpCode;
}
