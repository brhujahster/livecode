package co.inter.piggies.merchant.infra;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, UUID> {

    Optional<MerchantAccount> findByMerchantId(UUID merchantId);

    @Query("""
            UPDATE MerchantAccount a
               SET a.balance = a.balance + :amount, a.version = a.version + 1
             WHERE a.merchant.id = :merchantId""")
    int credit(UUID merchantId, long amount);
}
