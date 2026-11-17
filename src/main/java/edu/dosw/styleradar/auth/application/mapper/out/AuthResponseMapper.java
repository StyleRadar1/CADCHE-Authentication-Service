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
 * Mapper de salida (MapStruct): modelos del dominio → DTOs del contrato REST.
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
    @Mapping(target = "verificacionPendiente", source = "requiereOtp")
    @Mapping(target = "mensaje", expression = "java(sesion.requiereOtp() ? MENSAJE_OTP_ENVIADO : MENSAJE_SESION)")
    LoginResponseDTO toLoginResponse(SesionAutenticada sesion);

    @Mapping(target = "mensaje", constant = MENSAJE_REENVIO)
    OtpMensajeResponseDTO toOtpMensajeResponse(DesafioOtp desafio);

    @Mapping(target = "roles", source = "rol")
    UsuarioAuthResponseDTO toUsuarioAuthResponse(UsuarioRegistrado usuario);

    default Set<Rol> rolComoConjunto(Rol rol) {
        return rol == null ? Set.of() : Set.of(rol);
    }
}
