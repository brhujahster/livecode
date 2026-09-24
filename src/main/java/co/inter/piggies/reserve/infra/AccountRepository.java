package co.inter.piggies.reserve.infra;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAgencyAndNumber(String agency, String number);

    /**
     * Checa o disponível e reserva no mesmo comando, para que reservas simultâneas nunca
     * passem do disponível. Devolve 0 quando o saldo é insuficiente.
     */
    @Query("""
            UPDATE Account a
               SET a.reservedBalance = a.reservedBalance + :amount, a.version = a.version + 1
             WHERE a.id = :id AND a.balance - a.reservedBalance >= :amount""")
    int reserve(UUID id, long amount);

    @Query("""
            UPDATE Account a
               SET a.balance = a.balance - :amount, a.reservedBalance = a.reservedBalance - :amount,
                   a.version = a.version + 1
             WHERE a.id = :id""")
    int debitReserved(UUID id, long amount);

    @Query("""
            UPDATE Account a
               SET a.reservedBalance = a.reservedBalance - :amount, a.version = a.version + 1
             WHERE a.id = :id""")
    int releaseReserved(UUID id, long amount);
}
