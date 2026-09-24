package co.inter.piggies.merchant.repository;

import co.inter.piggies.merchant.entity.Merchant;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;



// Fornece operações de leitura e escrita na tabela de merchants.

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    // faz a busca de um merchant pelo cnpj, que é único
    Optional<Merchant> findByCnpj(String cnpj);
}

