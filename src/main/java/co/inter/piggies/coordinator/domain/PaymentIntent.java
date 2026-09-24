package co.inter.piggies.coordinator.domain;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(
        name = "payment_intent",
        schema = "spp_coordinator",
        check = @CheckConstraint(name = "ck_payment_intent_amount", constraint = "amount > 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentIntent {

    @Id
    private UUID id;

    @Column(name = "payer_cpf", nullable = false, length = 11)
    private String payerCpf;

    @Column(name = "payer_agency", nullable = false, length = 4)
    private String payerAgency;

    @Column(name = "payer_account_number", nullable = false, length = 20)
    private String payerAccountNumber;

    @Column(name = "merchant_cnpj", nullable = false, length = 14)
    private String merchantCnpj;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 32)
    private FailureReason failureReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PaymentIntent(UUID id, String payerCpf, String payerAgency, String payerAccountNumber,
                         String merchantCnpj, long amount) {
        this.id = id;
        this.payerCpf = payerCpf;
        this.payerAgency = payerAgency;
        this.payerAccountNumber = payerAccountNumber;
        this.merchantCnpj = merchantCnpj;
        this.amount = amount;
        this.stage = Stage.ACCEPTED;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }

    public PaymentStatus status() {
        return switch (stage) {
            case ACCEPTED, DEBITED -> PaymentStatus.PROCESSING;
            case CONFIRMED -> PaymentStatus.CONFIRMED;
            case FAILED -> PaymentStatus.FAILED;
        };
    }

    public void markDebited() {
        moveTo(Stage.DEBITED, Stage.ACCEPTED);
    }

    public void confirm() {
        moveTo(Stage.CONFIRMED, Stage.DEBITED);
    }

    /**
     * Só falha antes do débito: depois dele o dinheiro já saiu do cliente e não há estorno nesta versão.
     */
    public void fail(FailureReason reason) {
        moveTo(Stage.FAILED, Stage.ACCEPTED);
        this.failureReason = reason;
    }

    private void moveTo(Stage target, Stage required) {
        if (stage != required) {
            throw new IllegalStateException("Pagamento " + id + " está " + stage + " e não pode ir para " + target);
        }
        this.stage = target;
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
