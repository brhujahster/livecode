package co.inter.piggies.model;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Serdeable
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 4)
    private String agency;

    @Column(nullable = false, length = 20)
    private String number;

    @Column(nullable = false)
    private Long balance;

    @Column(nullable = false)
    private Long reservedBalance;
}
