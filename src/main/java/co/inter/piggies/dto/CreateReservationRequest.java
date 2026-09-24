package co.inter.piggies.dto;

import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

import java.util.UUID;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReservationRequest {
    private UUID paymentIntentId;
    private String payerCpf;
    private String payerAgency;
    private String payerAccount;
    private Long amount;
}
