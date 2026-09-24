package co.inter.piggies.coordinator.domain;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends CrudRepository<PaymentIntentEntity, UUID> {
}
