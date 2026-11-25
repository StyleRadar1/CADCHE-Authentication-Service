package edu.dosw.styleradar.auth.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;

@DisplayName("Rol: conversión del rol que devuelve ms-usuarios (RN-02)")
class RolTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "BUYER, BUYER",
            "comprador, BUYER",
            "ROLE_BUYER, BUYER",
            "ADMIN_ALMACEN, STORE_ADMIN",
            "tienda, STORE_ADMIN",
            "role_store_admin, STORE_ADMIN",
            "REPRESENTANTE_FUNDACION, FOUNDATION_REPRESENTATIVE",
            "FUNDACION, FOUNDATION_REPRESENTATIVE",
            "ADMIN_STYLERADAR, STYLERADAR_ADMIN",
            "' admin ', STYLERADAR_ADMIN"
    })
    void aceptaNombresEnInglesEspanolYConPrefijoRole(String recibido, Rol esperado) {
        assertEquals(esperado, Rol.desde(recibido));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "MODERADOR", "ROLE_"})
    void rolAusenteODesconocidoNoHabilitaSesion(String recibido) {
        assertThrows(CredencialesIncorrectasException.class, () -> Rol.desde(recibido));
    }

    @Test
    void cadaRolSeConvierteASiMismo() {
        for (Rol rol : Rol.values()) {
            assertEquals(rol, Rol.desde(rol.name()));
        }
    }
}
