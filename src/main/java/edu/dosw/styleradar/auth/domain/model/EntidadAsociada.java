package edu.dosw.styleradar.auth.domain.model;

/**
 * Identificadores de entidad asociada en ms-tiendas (storeId para STORE_ADMIN y
 * foundationId para FOUNDATION_REPRESENTATIVE) que se incluyen en el JWT emitido por ms-auth.
 */
public record EntidadAsociada(String storeId, Long foundationId) {

    public static final EntidadAsociada VACIA = new EntidadAsociada(null, null);

    public boolean estaVacia() {
        return (storeId == null || storeId.isBlank()) && foundationId == null;
    }
}
