package co.inter.piggies.merchant.infra;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "merchant_account",
        schema = "spp_merchant",
        uniqueConstraints = @UniqueConstraint(name = "uk_merchant_account_agency_number", columnNames = {"agency", "number"}),
        check = @CheckConstraint(name = "ck_merchant_account_balance", constraint = "balance >= 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantAccount {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    private Merchant merchant;

    @Column(nullable = false, length = 4)
    private String agency;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private long balance;

    @Version
    private Long version;

    public MerchantAccount(Merchant merchant, String agency, String number, long balance) {
        this.id = UUID.randomUUID();
        this.merchant = merchant;
        this.agency = agency;
        this.number = number;
        this.balance = balance;
    }
}
