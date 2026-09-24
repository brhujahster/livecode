package co.inter.piggies.merchant.dto;

import co.inter.piggies.merchant.entity.Merchant;
import co.inter.piggies.merchant.entity.MerchantStatus;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;


// Response com os dados do merchant e deve eetorna os dados completos quando o merchant é validado com sucesso.

@Serdeable
@Data
@Builder
public class MerchantResponse {

    private UUID id;
    private String name;
    private String cnpj;
    private String agency;
    private String accountNumber;
    private MerchantStatus status;


     // Factory method para converter uma entidade Merchant para MerchantResponse.

    public static MerchantResponse from(Merchant merchant) {
        return MerchantResponse.builder()
            .id(merchant.getId())
            .name(merchant.getName())
            .cnpj(merchant.getCnpj())
            .agency(merchant.getAgency())
            .accountNumber(merchant.getAccountNumber())
            .status(merchant.getStatus())
            .build();
    }
}

