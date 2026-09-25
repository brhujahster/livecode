package co.inter.piggies.merchant.web;

import co.inter.piggies.merchant.facade.ReceivableStatus;
import co.inter.piggies.merchant.facade.ReceivableView;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record ReceivableResponse(UUID paymentId, String merchantCnpj, long amount, ReceivableStatus status,
                                 Instant creditedAt) {

    static ReceivableResponse from(ReceivableView view) {
        return new ReceivableResponse(view.paymentId(), view.merchantCnpj(), view.amount(), view.status(),
                view.creditedAt());
    }
}
