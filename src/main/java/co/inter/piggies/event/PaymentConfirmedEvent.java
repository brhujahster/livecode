package co.inter.piggies.event;

import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfirmedEvent {
    private UUID paymentId;
    private String merchantCnpj;
    private Long amount;
    private Instant creditedAt;
}
