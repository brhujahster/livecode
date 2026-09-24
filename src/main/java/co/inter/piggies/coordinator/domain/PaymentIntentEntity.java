package co.inter.piggies.coordinator.domain;

import co.inter.piggies.coordinator.api.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "spp_coordinator", name = "payment_intent")
public class PaymentIntentEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payer_cpf", nullable = false, length = 11)
    private String payerCpf;

    @Column(name = "payer_agency", nullable = false, length = 4)
    private String payerAgency;

    @Column(name = "payer_account", nullable = false, length = 20)
    private String payerAccount;

    @Column(name = "merchant_cnpj", nullable = false, length = 14)
    private String merchantCnpj;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentIntentEntity() {
    }

    public static PaymentIntentEntity start(
            UUID id,
            String payerCpf,
            String payerAgency,
            String payerAccount,
            String merchantCnpj,
            Long amount,
            Instant now
    ) {
        if (id == null) {
            throw new InvalidPaymentException(FailureReasons.PAYMENT_ID_REQUIRED);
        }
        if (amount == null || amount < 1) {
            throw new InvalidPaymentException(FailureReasons.AMOUNT_INVALID);
        }
        var intent = new PaymentIntentEntity();
        intent.id = id;
        intent.payerCpf = payerCpf;
        intent.payerAgency = payerAgency;
        intent.payerAccount = payerAccount;
        intent.merchantCnpj = merchantCnpj;
        intent.amount = amount;
        intent.status = PaymentStatus.PROCESSING;
        intent.createdAt = now;
        intent.updatedAt = now;
        return intent;
    }

    public void attachReservation(UUID reservationId) {
        ensureProcessing();
        this.reservationId = reservationId;
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        ensureProcessing();
        this.status = PaymentStatus.CONFIRMED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        ensureProcessing();
        this.status = PaymentStatus.FAILED;
        this.failureReason = normalize(reason);
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPayerCpf() {
        return payerCpf;
    }

    public String getPayerAgency() {
        return payerAgency;
    }

    public String getPayerAccount() {
        return payerAccount;
    }

    public String getMerchantCnpj() {
        return merchantCnpj;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void ensureProcessing() {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Intenção " + id + " não pode mudar a partir de " + status);
        }
    }

    static String normalize(String reason) {
        if (reason == null || reason.isBlank()) {
            return FailureReasons.ORCHESTRATION_FAILED;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
