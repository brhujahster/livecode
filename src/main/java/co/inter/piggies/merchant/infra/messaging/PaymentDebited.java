package co.inter.piggies.merchant.infra.messaging;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento {@code payment-debited.v1.schema.json}, consumido pelo merchant. Cópia própria do módulo, sem
 * classe compartilhada com o coordinator.
 */
@Serdeable
public record PaymentDebited(UUID eventId, int eventVersion, UUID paymentId, String merchantCnpj, long amount,
                             Instant debitedAt) {
}
