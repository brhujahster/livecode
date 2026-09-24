package co.inter.piggies.repository;

import co.inter.piggies.model.PaymentIntent;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends CrudRepository<PaymentIntent, UUID> {
}
