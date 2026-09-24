package co.inter.piggies.dto;

import co.inter.piggies.model.PaymentStatus;
import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

import java.util.UUID;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAccepted {
    private UUID paymentId;
    private PaymentStatus status;
}
