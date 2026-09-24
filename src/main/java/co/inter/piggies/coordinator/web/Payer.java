package co.inter.piggies.coordinator.web;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Serdeable
public record Payer(
        @NotNull @Pattern(regexp = "^[0-9]{11}$") String cpf,
        @NotNull @Pattern(regexp = "^[0-9]{4}$") String agency,
        @NotNull @Pattern(regexp = "^[0-9]{1,20}$") String accountNumber) {
}
