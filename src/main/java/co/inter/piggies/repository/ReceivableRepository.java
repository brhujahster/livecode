package co.inter.piggies.repository;

import co.inter.piggies.model.Receivable;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivableRepository extends CrudRepository<Receivable, UUID> {
    Optional<Receivable> findByPaymentIntentId(UUID paymentIntentId);
}
