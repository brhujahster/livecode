package co.inter.piggies.merchant.infra.messaging;

import co.inter.piggies.merchant.facade.ReceivableView;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento {@code payment-confirmed.v1.schema.json}, publicado pelo merchant.
 */
@Serdeable
public record PaymentConfirmed(UUID eventId, int eventVersion, UUID paymentId, String merchantCnpj, long amount,
                               Instant creditedAt) {

    public static final int VERSION = 1;

    static PaymentConfirmed of(ReceivableView receivable) {
        return new PaymentConfirmed(UUID.randomUUID(), VERSION, receivable.paymentId(), receivable.merchantCnpj(),
                receivable.amount(), receivable.creditedAt());
    }
}
