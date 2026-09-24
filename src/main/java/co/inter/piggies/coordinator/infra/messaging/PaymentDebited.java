package co.inter.piggies.coordinator.infra.messaging;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento {@code payment-debited.v1.schema.json}, publicado pelo coordinator.
 */
@Serdeable
public record PaymentDebited(UUID eventId, int eventVersion, UUID paymentId, String merchantCnpj, long amount,
                             Instant debitedAt) {

    public static final int VERSION = 1;

    static PaymentDebited of(PaymentIntent debited) {
        return new PaymentDebited(UUID.randomUUID(), VERSION, debited.getId(), debited.getMerchantCnpj(),
                debited.getAmount(), debited.getUpdatedAt());
    }
}
