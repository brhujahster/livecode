package co.inter.piggies.service.impl;

import co.inter.piggies.dto.CreateReservationRequest;
import co.inter.piggies.model.Account;
import co.inter.piggies.model.Client;
import co.inter.piggies.model.Reservation;
import co.inter.piggies.model.ReservationStatus;
import co.inter.piggies.repository.AccountRepository;
import co.inter.piggies.repository.ClientRepository;
import co.inter.piggies.repository.ReservationRepository;
import co.inter.piggies.service.PiggiesReserveService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor
public class PiggiesReserveServiceImpl implements PiggiesReserveService {

    private final ReservationRepository reservationRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public Reservation createReservation(CreateReservationRequest request) {
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "amount deve ser um inteiro maior que zero");
        }

        Optional<Reservation> existing = reservationRepository.findByPaymentIntentId(request.getPaymentIntentId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Client client = clientRepository.findByCpf(request.getPayerCpf())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Não há conta para o CPF, agência e número informados"));

        Account account = accountRepository.findByClientIdAndAgencyAndNumber(client.getId(), request.getPayerAgency(), request.getPayerAccount())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Não há conta para o CPF, agência e número informados"));

        long available = account.getBalance() - account.getReservedBalance();
        if (available < request.getAmount()) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O disponível da conta é menor que o valor da reserva");
        }

        account.setReservedBalance(account.getReservedBalance() + request.getAmount());
        accountRepository.update(account);

        Reservation reservation = Reservation.builder()
                .paymentIntentId(request.getPaymentIntentId())
                .accountId(account.getId())
                .amount(request.getAmount())
                .status(ReservationStatus.RESERVED)
                .build();

        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public Reservation confirmReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Não existe reserva para o id informado"));

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return reservation;
        }

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "Reserva liberada não pode ser confirmada");
        }

        Account account = accountRepository.findById(reservation.getAccountId())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));

        account.setBalance(account.getBalance() - reservation.getAmount());
        account.setReservedBalance(account.getReservedBalance() - reservation.getAmount());
        accountRepository.update(account);

        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservationRepository.update(reservation);
    }

    @Override
    @Transactional
    public Reservation releaseReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Não existe reserva para o id informado"));

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return reservation;
        }

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "Reserva confirmada não pode ser liberada");
        }

        Account account = accountRepository.findById(reservation.getAccountId())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));

        account.setReservedBalance(account.getReservedBalance() - reservation.getAmount());
        accountRepository.update(account);

        reservation.setStatus(ReservationStatus.RELEASED);
        return reservationRepository.update(reservation);
    }
}
