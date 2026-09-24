package co.inter.piggies.merchant.infra;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.UUID;

@Repository
public interface ReceivableRepository extends JpaRepository<Receivable, UUID> {
}
