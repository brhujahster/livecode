package co.inter.piggies.repository;

import co.inter.piggies.model.Merchant;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends CrudRepository<Merchant, UUID> {
    Optional<Merchant> findByCnpj(String cnpj);
}
