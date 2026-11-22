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
@Schema(description = "Request a new OTP code for the registered email")
public class ReenviarOtpRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 254, message = "El correo electrónico excede la longitud máxima permitida")
    @Schema(description = "Registered email that needs a new OTP code", example = "buyer@styleradar.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonDeserialize(using = RecortarEspaciosDeserializer.class)
    private String email;
}
