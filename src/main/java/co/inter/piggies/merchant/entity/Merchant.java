package co.inter.piggies.merchant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;


 //Entidade que representa um comerciante no sistema.
// O CNPJ é único no sistema e o  status determina se o merchant pode receber créditos.
@Entity
@Table(name = "merchant", schema = "spp_merchant", uniqueConstraints = {
    @UniqueConstraint(columnNames = "cnpj", name = "uk_merchant_cnpj")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(nullable = false, length = 10)
    private String agency;

    @Column(nullable = false, length = 20)
    private String accountNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MerchantStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

