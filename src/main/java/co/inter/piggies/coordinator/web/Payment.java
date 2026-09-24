package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.FailureReason;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record Payment(
        UUID paymentId,
        PaymentStatus status,
        QrCode qrCode,
        Payer payer,
        @Nullable FailureReason failureReason,
        Instant createdAt,
        Instant updatedAt) {

    static Payment from(PaymentIntent intent) {
        return new Payment(
                intent.getId(),
                intent.status(),
                new QrCode(intent.getMerchantCnpj(), intent.getAmount()),
                new Payer(intent.getPayerCpf(), intent.getPayerAgency(), intent.getPayerAccountNumber()),
                intent.getFailureReason(),
                intent.getCreatedAt(),
                intent.getUpdatedAt());
    }
}
