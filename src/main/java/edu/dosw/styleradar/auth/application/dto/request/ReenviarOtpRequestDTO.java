package edu.dosw.styleradar.auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de generación y reenvío de un nuevo código OTP al correo registrado (SR-RF-92)")
public class ReenviarOtpRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Schema(description = "Correo electrónico registrado que requiere un nuevo código OTP", example = "comprador@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
}

