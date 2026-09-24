package co.inter.piggies.coordinator.infra.persistence;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;

import java.util.Optional;
import java.util.UUID;

@Singleton
class JpaPaymentIntentStore implements PaymentIntentStore {

    private final PaymentIntentRepository repository;
    private final EntityManager entityManager;

    JpaPaymentIntentStore(PaymentIntentRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<PaymentIntent> findById(UUID id) {
        return repository.findById(id);
    }

    /**
     * {@code ON CONFLICT DO NOTHING} em vez de {@code persist}: com duas transações gravando o mesmo id, o Postgres
     * faz a segunda esperar a primeira e devolve 0 linhas, sem violação de chave que derrubaria a transação.
     */
    @Override
    public boolean insertIfAbsent(PaymentIntent intent) {
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO spp_coordinator.payment_intent
                            (id, payer_cpf, payer_agency, payer_account_number, merchant_cnpj, amount, stage,
                             version, created_at, updated_at)
                        VALUES (:id, :cpf, :agency, :account, :cnpj, :amount, :stage, 0, :createdAt, :updatedAt)
                        ON CONFLICT (id) DO NOTHING""")
                .setParameter("id", intent.getId())
                .setParameter("cpf", intent.getPayerCpf())
                .setParameter("agency", intent.getPayerAgency())
                .setParameter("account", intent.getPayerAccountNumber())
                .setParameter("cnpj", intent.getMerchantCnpj())
                .setParameter("amount", intent.getAmount())
                .setParameter("stage", intent.getStage().name())
                .setParameter("createdAt", intent.getCreatedAt())
                .setParameter("updatedAt", intent.getUpdatedAt())
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    public PaymentIntent update(PaymentIntent intent) {
        return repository.update(intent);
    }
}
