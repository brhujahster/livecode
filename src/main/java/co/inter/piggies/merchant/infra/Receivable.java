package co.inter.piggies.merchant.infra;

import co.inter.piggies.merchant.facade.ReceivableStatus;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(
        name = "receivable",
        schema = "spp_merchant",
        check = @CheckConstraint(name = "ck_receivable_amount", constraint = "amount > 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receivable {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReceivableStatus status;

    @Column(name = "credited_at", nullable = false)
    private Instant creditedAt;

    public Receivable(UUID paymentId, Merchant merchant, long amount) {
        this.paymentId = paymentId;
        this.merchant = merchant;
        this.amount = amount;
        this.status = ReceivableStatus.CREDITED;
        // Precisão do Postgres: a confirmação republicada lê do banco e precisa trazer o mesmo creditedAt.
        this.creditedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
