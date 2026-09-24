package co.inter.piggies.coordinator.domain;

import java.util.UUID;

/**
 * A Idempotency-Key já foi usada para um pagamento com outros dados.
 */
public class IdempotencyConflictException extends RuntimeException {

    private final UUID paymentId;

    public IdempotencyConflictException(UUID paymentId) {
        super("A Idempotency-Key " + paymentId + " já foi usada com outros dados");
        this.paymentId = paymentId;
    }

    public UUID paymentId() {
        return paymentId;
    }
}
