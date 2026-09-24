package co.inter.piggies.controller;

import co.inter.piggies.dto.CreateReservationRequest;
import co.inter.piggies.model.Reservation;
import co.inter.piggies.service.PiggiesReserveService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Controller("/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final PiggiesReserveService reserveService;

    @Post
    public HttpResponse<Reservation> createReservation(@Body CreateReservationRequest request) {
        Reservation reservation = reserveService.createReservation(request);
        return HttpResponse.created(reservation);
    }

    @Post("/{id}/confirm")
    public HttpResponse<Reservation> confirmReservation(@PathVariable UUID id) {
        Reservation reservation = reserveService.confirmReservation(id);
        return HttpResponse.ok(reservation);
    }

    @Post("/{id}/release")
    public HttpResponse<Reservation> releaseReservation(@PathVariable UUID id) {
        Reservation reservation = reserveService.releaseReservation(id);
        return HttpResponse.ok(reservation);
    }
}
