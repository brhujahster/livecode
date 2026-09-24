package co.inter.piggies.event;

import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

import java.util.UUID;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfirmEvent {
    private UUID paymentId;
    private UUID reservationId;
    private String merchantCnpj;
    private Long amount;
}
