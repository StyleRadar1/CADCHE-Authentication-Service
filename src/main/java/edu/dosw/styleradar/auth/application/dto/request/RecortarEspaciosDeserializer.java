package edu.dosw.styleradar.auth.application.dto.request;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Quita los espacios al inicio y al final antes de validar (contrato 3.2: el correo "se guarda en minúsculas y
 * sin espacios"). Así " Ana@Correo.com " se acepta y se normaliza en lugar de rechazarse por formato.
 */
public class RecortarEspaciosDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String valor = parser.getValueAsString();
        return valor == null ? null : valor.strip();
    }
}
