package co.inter.piggies.coordinator.infra.local;

import co.inter.piggies.coordinator.domain.FailureReason;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.ReserveGateway;
import co.inter.piggies.reserve.facade.ReserveCommand;
import co.inter.piggies.reserve.facade.ReserveFacade;
import co.inter.piggies.reserve.facade.ReserveResult;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

/**
 * Chama o módulo reserve no mesmo processo. Na extração, vira um cliente HTTP de reserve.openapi.yaml.
 */
@Singleton
class LocalReserveGateway implements ReserveGateway {

    private final ReserveFacade reserve;

    LocalReserveGateway(ReserveFacade reserve) {
        this.reserve = reserve;
    }

    @Override
    public Optional<FailureReason> reserve(PaymentIntent intent) {
        ReserveResult result = reserve.reserve(new ReserveCommand(
                intent.getId(),
                intent.getPayerCpf(),
                intent.getPayerAgency(),
                intent.getPayerAccountNumber(),
                intent.getAmount()));
        return switch (result) {
            case ReserveResult.Reserved reserved -> Optional.empty();
            case ReserveResult.Rejected rejected -> Optional.of(switch (rejected.reason()) {
                case PAYER_ACCOUNT_NOT_FOUND -> FailureReason.PAYER_ACCOUNT_NOT_FOUND;
                case INSUFFICIENT_BALANCE -> FailureReason.INSUFFICIENT_BALANCE;
            });
        };
    }

    @Override
    public void confirm(UUID paymentId) {
        reserve.confirm(paymentId);
    }

    @Override
    public void release(UUID paymentId) {
        reserve.release(paymentId);
    }
}
