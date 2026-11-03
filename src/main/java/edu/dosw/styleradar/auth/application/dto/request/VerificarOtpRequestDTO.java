package edu.dosw.styleradar.auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de verificación de código OTP de 6 dígitos enviado al correo electrónico (SR-RF-92)")
public class VerificarOtpRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Schema(description = "Correo electrónico asociado al desafío OTP activo", example = "comprador@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "El código OTP es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El código OTP debe contener exactamente 6 dígitos numéricos")
    @Schema(description = "Código numérico de un solo uso de 6 dígitos con vigencia de 5 minutos", example = "482915", requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigoOtp;
}

