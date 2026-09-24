package co.inter.piggies.coordinator.web;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Serdeable
public record CreatePaymentRequest(@NotNull @Valid QrCode qrCode, @NotNull @Valid Payer payer) {
}
