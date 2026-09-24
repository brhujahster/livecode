package co.inter.piggies.repository;

import co.inter.piggies.model.Account;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends CrudRepository<Account, UUID> {
    Optional<Account> findByClientIdAndAgencyAndNumber(UUID clientId, String agency, String number);
}
