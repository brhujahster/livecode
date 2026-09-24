package co.inter.piggies.service;

import co.inter.piggies.dto.CreateReservationRequest;
import co.inter.piggies.model.Reservation;

import java.util.UUID;

public interface PiggiesReserveService {
    Reservation createReservation(CreateReservationRequest request);
    Reservation confirmReservation(UUID reservationId);
    Reservation releaseReservation(UUID reservationId);
}
