package co.inter.piggies.reserve.web;

import co.inter.piggies.reserve.facade.ReservationNotFoundException;
import co.inter.piggies.reserve.facade.ReserveCommand;
import co.inter.piggies.reserve.facade.ReserveFacade;
import co.inter.piggies.reserve.facade.ReserveResult;
import co.inter.piggies.reserve.facade.ReserveResult.RejectionReason;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;

import java.util.UUID;

/**
 * API interna de {@code reserve.openapi.yaml}. Hoje o coordinator chama a fachada direto; esta API é o que ele
 * passa a chamar quando o reserve virar um serviço separado.
 */
@Controller("/v1/reservations")
class ReservationsController {

    private final ReserveFacade reserve;

    ReservationsController(ReserveFacade reserve) {
        this.reserve = reserve;
    }

    @Put("/{paymentId}")
    HttpResponse<?> reserve(UUID paymentId, @Body @Valid ReserveRequest request) {
        ReserveResult result = reserve.reserve(new ReserveCommand(
                paymentId, request.cpf(), request.agency(), request.accountNumber(), request.amount()));
        return switch (result) {
            case ReserveResult.Reserved reserved -> HttpResponse
                    .status(reserved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(ReservationResponse.from(reserved.reservation()));
            case ReserveResult.Rejected rejected -> rejection(rejected.reason());
        };
    }

    @Get("/{paymentId}")
    ReservationResponse get(UUID paymentId) {
        return reserve.findReservation(paymentId)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new ReservationNotFoundException(paymentId));
    }

    @Post("/{paymentId}/confirm")
    ReservationResponse confirm(UUID paymentId) {
        return ReservationResponse.from(reserve.confirm(paymentId));
    }

    @Post("/{paymentId}/release")
    ReservationResponse release(UUID paymentId) {
        return ReservationResponse.from(reserve.release(paymentId));
    }

    private static HttpResponse<ReserveProblem> rejection(RejectionReason reason) {
        String title = switch (reason) {
            case INSUFFICIENT_BALANCE -> "Saldo insuficiente";
            case PAYER_ACCOUNT_NOT_FOUND -> "Conta do pagador não encontrada";
        };
        return ReserveProblem.response(HttpStatus.UNPROCESSABLE_ENTITY, title, null, reason.name());
    }
}
