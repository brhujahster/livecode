package co.inter.piggies.reserve.infra;

import co.inter.piggies.reserve.facade.ReservationStatus;
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
import java.util.UUID;

@Entity
@Table(
        name = "reservation",
        schema = "spp_reserve",
        check = @CheckConstraint(name = "ck_reservation_amount", constraint = "amount > 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Reservation(UUID paymentId, Account account, long amount) {
        this.paymentId = paymentId;
        this.account = account;
        this.amount = amount;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void confirm() {
        moveTo(ReservationStatus.CONFIRMED);
    }

    public void release() {
        moveTo(ReservationStatus.RELEASED);
    }

    private void moveTo(ReservationStatus target) {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Reserva " + paymentId + " está " + status + " e não pode ir para " + target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }
}
