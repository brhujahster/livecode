package co.inter.piggies.coordinator.infra.messaging;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento {@code payment-confirmed.v1.schema.json}, consumido pelo coordinator. Cópia própria do módulo, sem
 * classe compartilhada com o merchant.
 */
@Serdeable
public record PaymentConfirmed(UUID eventId, int eventVersion, UUID paymentId, String merchantCnpj, long amount,
                               Instant creditedAt) {
}
