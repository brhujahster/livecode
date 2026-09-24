package co.inter.piggies.reserve.facade;

public class ReservationConflictException extends RuntimeException {

    public enum Code {
        INVALID_RESERVATION_TRANSITION,
        IDEMPOTENCY_CONFLICT
    }

    private final Code code;

    public ReservationConflictException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
