package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * Excepción base del dominio de autenticación. Transporta el código del catálogo de errores
 * y los datos que el front necesita para reaccionar (intentos restantes y segundos de espera),
 * sin acoplarse a HTTP.
 */
public abstract class AuthDomainException extends RuntimeException {

    private final CodigoError codigo;
    private final Integer intentosRestantes;
    private final Long esperarSegundos;

    protected AuthDomainException(String message, CodigoError codigo) {
        this(message, codigo, null, null, null);
    }

    protected AuthDomainException(String message, CodigoError codigo, Throwable cause) {
        this(message, codigo, null, null, cause);
    }

    protected AuthDomainException(
            String message,
            CodigoError codigo,
            Integer intentosRestantes,
            Long esperarSegundos,
            Throwable cause) {
        super(message, cause);
        this.codigo = codigo;
        this.intentosRestantes = intentosRestantes;
        this.esperarSegundos = esperarSegundos;
    }

    public CodigoError getCodigo() {
        return codigo;
    }

    public Integer getIntentosRestantes() {
        return intentosRestantes;
    }

    public Long getEsperarSegundos() {
        return esperarSegundos;
    }
}
