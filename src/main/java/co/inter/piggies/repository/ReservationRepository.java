package co.inter.piggies.repository;

import co.inter.piggies.model.Reservation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends CrudRepository<Reservation, UUID> {
    Optional<Reservation> findByPaymentIntentId(UUID paymentIntentId);
}
