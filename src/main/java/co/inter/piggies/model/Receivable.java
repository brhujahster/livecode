package co.inter.piggies.model;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Serdeable
@Entity
@Table(name = "receivables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receivable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID paymentIntentId;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceivableStatus status;

    @Column(nullable = false)
    private Instant creditedAt;

    @PrePersist
    public void prePersist() {
        if (creditedAt == null) {
            creditedAt = Instant.now();
        }
        if (status == null) {
            status = ReceivableStatus.CREDITED;
        }
    }
}
