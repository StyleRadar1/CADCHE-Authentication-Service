package edu.dosw.styleradar.auth.domain.exception;

public class OtpBloqueadoTemporalmenteException extends RuntimeException {

    private final long segundosRestantes;

    public OtpBloqueadoTemporalmenteException(String message, long segundosRestantes) {
        super(message);
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
