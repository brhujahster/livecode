package co.inter.piggies.reserve.web;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Serdeable
public record ReserveRequest(
        @NotNull @Pattern(regexp = "^[0-9]{11}$") String cpf,
        @NotNull @Pattern(regexp = "^[0-9]{4}$") String agency,
        @NotNull @Pattern(regexp = "^[0-9]{1,20}$") String accountNumber,
        @NotNull @Min(1) Long amount) {
}
