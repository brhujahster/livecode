package co.inter.piggies.repository;

import co.inter.piggies.model.Client;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends CrudRepository<Client, UUID> {
    Optional<Client> findByCpf(String cpf);
}
