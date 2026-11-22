package edu.dosw.styleradar.auth.application.mapper.out;

import java.util.Set;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.OtpMensajeResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.UsuarioAuthResponseDTO;
import edu.dosw.styleradar.auth.domain.model.DesafioOtp;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;
import edu.dosw.styleradar.auth.domain.model.UsuarioRegistrado;

/**
 * Output mapper (MapStruct): domain models to REST DTOs (English field names, Spanish user messages).
 */
@Mapper(componentModel = "spring")
public interface AuthResponseMapper {

    String TOKEN_TYPE = "Bearer";
    String MENSAJE_SESION = "Inicio de sesión exitoso";
    String MENSAJE_OTP_ENVIADO = "Código de verificación OTP enviado a su correo electrónico";
    String MENSAJE_REENVIO = "Se envió un código de verificación de 6 dígitos a su correo registrado";

    @Mapping(target = "tokenType", constant = TOKEN_TYPE)
    @Mapping(target = "expiresIn", source = "expiraEnSegundos")
    @Mapping(target = "roles", source = "rol")
    @Mapping(target = "userId", source = "usuarioId")
    @Mapping(target = "emailVerified", source = "correoVerificado")
    @Mapping(target = "otpRequired", source = "requiereOtp")
    @Mapping(target = "otpExpiresInSeconds", source = "otpExpiraEnSegundos")
    @Mapping(target = "otpAttemptsRemaining", source = "intentosOtpRestantes")
    @Mapping(target = "message", expression = "java(sesion.requiereOtp() ? MENSAJE_OTP_ENVIADO : MENSAJE_SESION)")
    LoginResponseDTO toLoginResponse(SesionAutenticada sesion);

    @Mapping(target = "message", constant = MENSAJE_REENVIO)
    @Mapping(target = "expiresInSeconds", source = "expiraEnSegundos")
    @Mapping(target = "attemptsRemaining", source = "intentosRestantes")
    @Mapping(target = "resendsRemaining", source = "reenviosRestantes")
    OtpMensajeResponseDTO toOtpMensajeResponse(DesafioOtp desafio);

    @Mapping(target = "roles", source = "rol")
    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "phone", source = "telefono")
    @Mapping(target = "registeredAt", source = "fechaRegistro")
    UsuarioAuthResponseDTO toUsuarioAuthResponse(UsuarioRegistrado usuario);

    default Set<Rol> rolComoConjunto(Rol rol) {
        return rol == null ? Set.of() : Set.of(rol);
    }
}
