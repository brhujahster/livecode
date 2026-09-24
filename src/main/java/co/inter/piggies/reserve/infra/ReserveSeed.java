package co.inter.piggies.reserve.infra;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
@Requires(property = "spp.seed.enabled", notEquals = "false")
public class ReserveSeed {

    private final ClientRepository clients;
    private final AccountRepository accounts;

    public ReserveSeed(ClientRepository clients, AccountRepository accounts) {
        this.clients = clients;
        this.accounts = accounts;
    }

    @EventListener
    @Transactional
    public void onStartup(StartupEvent event) {
        seed();
    }

    @Transactional
    public void seed() {
        seedClient("Cliente A", "12345678901", "0001", "000123", 500);
        seedClient("Cliente B", "98765432100", "0001", "000456", 50);
    }

    private void seedClient(String name, String cpf, String agency, String number, long balance) {
        if (clients.findByCpf(cpf).isPresent()) {
            return;
        }
        Client client = clients.save(new Client(name, cpf));
        accounts.save(new Account(client, agency, number, balance));
    }
}
