package co.inter.piggies.reserve.web;

import co.inter.piggies.reserve.facade.ReservationConflictException;
import co.inter.piggies.reserve.facade.ReservationNotFoundException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

final class ReservationExceptionHandlers {

    private ReservationExceptionHandlers() {
    }

    @Produces
    @Singleton
    static class NotFound implements ExceptionHandler<ReservationNotFoundException, HttpResponse<ReserveProblem>> {

        @Override
        public HttpResponse<ReserveProblem> handle(HttpRequest request, ReservationNotFoundException exception) {
            return ReserveProblem.response(HttpStatus.NOT_FOUND, "Reserva não encontrada", exception.getMessage(),
                    "RESERVATION_NOT_FOUND");
        }
    }

    @Produces
    @Singleton
    static class Conflict implements ExceptionHandler<ReservationConflictException, HttpResponse<ReserveProblem>> {

        @Override
        public HttpResponse<ReserveProblem> handle(HttpRequest request, ReservationConflictException exception) {
            String title = switch (exception.code()) {
                case INVALID_RESERVATION_TRANSITION -> "Transição de reserva inválida";
                case IDEMPOTENCY_CONFLICT -> "Reserva existente com outros dados";
            };
            return ReserveProblem.response(HttpStatus.CONFLICT, title, exception.getMessage(), exception.code().name());
        }
    }
}
