package co.inter.piggies.reserve.infra;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "account",
        schema = "spp_reserve",
        uniqueConstraints = @UniqueConstraint(name = "uk_account_agency_number", columnNames = {"agency", "number"}),
        check = {
                @CheckConstraint(name = "ck_account_balance", constraint = "balance >= 0"),
                @CheckConstraint(name = "ck_account_reserved", constraint = "reserved_balance >= 0 AND reserved_balance <= balance")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 4)
    private String agency;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private long balance;

    @Column(name = "reserved_balance", nullable = false)
    private long reservedBalance;

    @Version
    private Long version;

    public Account(Client client, String agency, String number, long balance) {
        this.id = UUID.randomUUID();
        this.client = client;
        this.agency = agency;
        this.number = number;
        this.balance = balance;
        this.reservedBalance = 0;
    }

    public long availableBalance() {
        return balance - reservedBalance;
    }
}
