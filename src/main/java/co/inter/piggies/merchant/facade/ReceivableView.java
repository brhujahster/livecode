package co.inter.piggies.merchant.facade;

import java.time.Instant;
import java.util.UUID;

public record ReceivableView(UUID paymentId, String merchantCnpj, long amount, ReceivableStatus status,
                             Instant creditedAt) {
}
