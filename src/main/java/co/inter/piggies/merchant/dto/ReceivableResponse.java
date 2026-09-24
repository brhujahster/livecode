package co.inter.piggies.merchant.dto;

import co.inter.piggies.merchant.entity.Receivable;
import co.inter.piggies.merchant.entity.ReceivableStatus;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;


// Response com os dados do recebível e vai retornar os dados do recebível quando o crédito é registrado com sucesso.

@Serdeable
@Data
@Builder
public class ReceivableResponse {

    private UUID id;
    private UUID paymentIntentId;
    private UUID merchantId;
    private Long amount;
    private ReceivableStatus status;
    private Instant creditedAt;


    // converter uma entidade Receivable para ReceivableResponse.

    public static ReceivableResponse from(Receivable receivable) {
        return ReceivableResponse.builder()
            .id(receivable.getId())
            .paymentIntentId(receivable.getPaymentIntentId())
            .merchantId(receivable.getMerchantId())
            .amount(receivable.getAmount())
            .status(receivable.getStatus())
            .creditedAt(receivable.getCreditedAt())
            .build();
    }
}

