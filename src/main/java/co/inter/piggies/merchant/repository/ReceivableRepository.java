package co.inter.piggies.merchant.repository;

import co.inter.piggies.merchant.entity.Receivable;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


// Repositório para a entidade de recebíveis.

@Repository
public interface ReceivableRepository extends JpaRepository<Receivable, UUID> {

    //Aqui vai buscar um recebível pelo paymentIntentId que é único e retorna um Optional<Receivable>
    // que pode estar vazio se não houver nenhum recebível com o paymentIntentId fornecido.

    Optional<Receivable> findByPaymentIntentId(UUID paymentIntentId);
}

