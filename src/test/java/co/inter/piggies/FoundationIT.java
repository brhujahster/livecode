package co.inter.piggies;

import co.inter.piggies.merchant.infra.MerchantAccountRepository;
import co.inter.piggies.merchant.infra.MerchantRepository;
import co.inter.piggies.merchant.infra.MerchantSeed;
import co.inter.piggies.merchant.facade.MerchantStatus;
import co.inter.piggies.reserve.infra.AccountRepository;
import co.inter.piggies.reserve.infra.ClientRepository;
import co.inter.piggies.reserve.infra.ReserveSeed;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(transactional = false)
class FoundationIT extends AbstractContainersTest {

    @Inject
    ClientRepository clients;

    @Inject
    AccountRepository accounts;

    @Inject
    MerchantRepository merchants;

    @Inject
    MerchantAccountRepository merchantAccounts;

    @Inject
    ReserveSeed reserveSeed;

    @Inject
    MerchantSeed merchantSeed;

    @Test
    void createsOneSchemaPerModule() throws SQLException {
        assertThat(schemas()).contains("spp_coordinator", "spp_reserve", "spp_merchant");
    }

    @Test
    void seedsClientsWithAccounts() {
        var clientA = accounts.findByAgencyAndNumber("0001", "000123").orElseThrow();
        assertThat(clientA.getBalance()).isEqualTo(500);
        assertThat(clientA.getReservedBalance()).isZero();

        var clientB = accounts.findByAgencyAndNumber("0001", "000456").orElseThrow();
        assertThat(clientB.getBalance()).isEqualTo(50);

        assertThat(clients.findByCpf("12345678901")).isPresent();
        assertThat(clients.findByCpf("98765432100")).isPresent();
    }

    @Test
    void seedsActiveAndInactiveMerchants() {
        var merchantX = merchants.findByCnpj("12345678000199").orElseThrow();
        assertThat(merchantX.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(merchantAccounts.findByMerchantId(merchantX.getId()).orElseThrow().getBalance()).isZero();

        var merchantY = merchants.findByCnpj("98765432000155").orElseThrow();
        assertThat(merchantY.getStatus()).isEqualTo(MerchantStatus.INACTIVE);
    }

    @Test
    void seedingAgainDoesNotDuplicate() {
        reserveSeed.seed();
        merchantSeed.seed();

        assertThat(clients.count()).isEqualTo(2);
        assertThat(accounts.count()).isEqualTo(2);
        assertThat(merchants.count()).isEqualTo(2);
        assertThat(merchantAccounts.count()).isEqualTo(2);
    }

    @Test
    void databaseRejectsNegativeOrOverReservedBalances() throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE spp_reserve.account SET balance = -1 WHERE number = '000123'"))
                    .hasMessageContaining("ck_account_balance");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE spp_reserve.account SET reserved_balance = balance + 1 WHERE number = '000123'"))
                    .hasMessageContaining("ck_account_reserved");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE spp_merchant.merchant_account SET balance = -1 WHERE number = '900001'"))
                    .hasMessageContaining("ck_merchant_account_balance");
        }
    }

    private List<String> schemas() throws SQLException {
        List<String> names = new ArrayList<>();
        try (var connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             var result = connection.createStatement().executeQuery("SELECT schema_name FROM information_schema.schemata")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }
}
