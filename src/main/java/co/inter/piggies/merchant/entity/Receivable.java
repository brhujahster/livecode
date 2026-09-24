package co.inter.piggies.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

// Recebível creditado para um merchant e deve ser persistida em spp_merchant.
// O paymentIntentId é único no sistema, servindo como chave de idempotência.
// Tentar creditar duas vezes com o mesmo paymentIntentId retorna o recebível existente.

@Entity
@Table(name = "receivable", schema = "spp_merchant", uniqueConstraints = {
    @UniqueConstraint(columnNames = "payment_intent_id", name = "uk_receivable_payment_intent_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receivable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID paymentIntentId;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID merchantId;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReceivableStatus status;

    @Column(nullable = false)
    private Instant creditedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

