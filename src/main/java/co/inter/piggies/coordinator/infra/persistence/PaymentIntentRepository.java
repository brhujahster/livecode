package co.inter.piggies.coordinator.infra.persistence;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
}
