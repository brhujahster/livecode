package co.inter.piggies.coordinator.support;

import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.orchestration.MerchantOutcome;
import co.inter.piggies.coordinator.orchestration.ReserveOutcome;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoordinatorScripts {

    public static final ReserveScript reserve = new ReserveScript();
    public static final MerchantScript merchant = new MerchantScript();
    public static final AtomicInteger published = new AtomicInteger();

    private CoordinatorScripts() {
    }

    public static void reset() {
        reserve.reset();
        merchant.reset();
        published.set(0);
    }

    public static final class ReserveScript {
        public volatile CountDownLatch gate = new CountDownLatch(0);
        public volatile ReserveOutcome outcome = ReserveOutcome.reserved(UUID.randomUUID());
        public final AtomicInteger entered = new AtomicInteger();
        public final AtomicInteger finished = new AtomicInteger();
        public final AtomicInteger confirms = new AtomicInteger();
        public final AtomicInteger releases = new AtomicInteger();

        public ReserveOutcome reserve(PaymentIntentEntity intent) {
            entered.incrementAndGet();
            try {
                if (!gate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Tempo esgotado ao esperar a reserva");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Reserva interrompida", exception);
            }
            finished.incrementAndGet();
            return outcome;
        }

        private void reset() {
            gate = new CountDownLatch(0);
            outcome = ReserveOutcome.reserved(UUID.randomUUID());
            entered.set(0);
            finished.set(0);
            confirms.set(0);
            releases.set(0);
        }
    }

    public static final class MerchantScript {
        public volatile MerchantOutcome outcome = MerchantOutcome.accepted();
        public final AtomicInteger calls = new AtomicInteger();

        public MerchantOutcome validate(String merchantCnpj) {
            calls.incrementAndGet();
            return outcome;
        }

        private void reset() {
            outcome = MerchantOutcome.accepted();
            calls.set(0);
        }
    }
}
