package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.model.SolicitudRestablecimiento;
import edu.dosw.styleradar.auth.domain.port.in.RestablecerContrasenaUseCase;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.auth.infrastructure.exception.GlobalExceptionHandler;

@WebMvcTest(controllers = RestablecerContrasenaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
@DisplayName("RestablecerContrasenaController: rutas públicas, validaciones y errores ProblemDetail")
class RestablecerContrasenaControllerTest {

    private static final String JSON_RESET =
            "{\"email\":\"buyer@styleradar.com\",\"otpCode\":\"482915\",\"newPassword\":\"NuevaClave2026*\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestablecerContrasenaUseCase useCase;
    @MockitoBean
    private UsuarioClient usuarioClient;
    @MockitoBean
    private OtpService otpService;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @Test
    void solicitarEsPublicoYRespondeSiempre202() throws Exception {
        when(useCase.solicitar("buyer@styleradar.com"))
                .thenReturn(new SolicitudRestablecimiento("buyer@styleradar.com", 300L));

        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" buyer@styleradar.com \"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(RestablecerContrasenaController.MENSAJE_SOLICITUD))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
    }

    @Test
    void restablecerRespondeSinTokenYSinDatosDeLaCuenta() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(JSON_RESET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(RestablecerContrasenaController.MENSAJE_CAMBIADA))
                .andExpect(jsonPath("$.expiresInSeconds").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(useCase).restablecer("buyer@styleradar.com", "482915", "NuevaClave2026*");
    }

    @Test
    void contrasenaDebilOCodigoMalFormadoSon400SinLlamarAlCasoDeUso() throws Exception {
        for (String cuerpo : new String[] {
                "{\"email\":\"buyer@styleradar.com\",\"otpCode\":\"482915\",\"newPassword\":\"solominusculas1\"}",
                // Igual que ms-usuarios (CredentialComplexityPolicy): el espacio no cuenta como símbolo.
                "{\"email\":\"buyer@styleradar.com\",\"otpCode\":\"482915\",\"newPassword\":\"Abcdefg1 \"}",
                "{\"email\":\"buyer@styleradar.com\",\"otpCode\":\"48291\",\"newPassword\":\"NuevaClave2026*\"}",
                "{\"email\":\"no-es-correo\",\"otpCode\":\"482915\",\"newPassword\":\"NuevaClave2026*\"}"}) {
            mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATA"));
        }
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"buyer@styleradar.com\",\"otpCode\":\"482915\",\"newPassword\":\"fkzwdupLPn3FhE9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("carácter especial")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("carácter especial")))
                .andExpect(jsonPath("$.errors.newPassword").value(org.hamcrest.Matchers.containsString("carácter especial")));
        verify(useCase, never()).restablecer(anyString(), anyString(), anyString());
    }

    @Test
    void erroresDelDominioSalenConSuCodigo() throws Exception {
        doThrow(new OtpExpiradoException("El código expiró o no existe. Solicita uno nuevo."))
                .when(useCase).restablecer("buyer@styleradar.com", "482915", "NuevaClave2026*");
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(JSON_RESET))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

        doThrow(new CuentaNoActivaException("La cuenta se encuentra inactiva o bloqueada"))
                .when(useCase).restablecer("buyer@styleradar.com", "482915", "NuevaClave2026*");
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(JSON_RESET))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }
}
