package co.inter.piggies.contracts;

import co.inter.piggies.coordinator.infra.messaging.PaymentDebited;
import co.inter.piggies.merchant.infra.messaging.PaymentConfirmed;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os eventos que cada módulo publica precisam passar no JSON Schema do contrato, e cada consumidor precisa ler o
 * exemplo do contrato com a sua própria cópia do record.
 */
class EventSchemaTest {

    private static final Path EVENTS = Path.of("specs/001-pagamento-piggies/contracts/events");
    private static final String DEBITED_SCHEMA = "payment-debited.v1.schema.json";
    private static final String CONFIRMED_SCHEMA = "payment-confirmed.v1.schema.json";

    private final ObjectMapper json = ObjectMapper.getDefault();

    @Test
    void coordinatorPublishesDebitedEventsThatMatchTheSchema() throws IOException {
        var event = new PaymentDebited(UUID.randomUUID(), PaymentDebited.VERSION, UUID.randomUUID(),
                "12345678000199", 100, Instant.now());

        assertThat(errors(DEBITED_SCHEMA, json.writeValueAsString(event))).isEmpty();
    }

    @Test
    void merchantPublishesConfirmedEventsThatMatchTheSchema() throws IOException {
        var event = new PaymentConfirmed(UUID.randomUUID(), PaymentConfirmed.VERSION, UUID.randomUUID(),
                "12345678000199", 100, Instant.now());

        assertThat(errors(CONFIRMED_SCHEMA, json.writeValueAsString(event))).isEmpty();
    }

    @Test
    void merchantReadsTheDebitedExampleOfTheContract() throws IOException {
        var event = json.readValue(example(DEBITED_SCHEMA), co.inter.piggies.merchant.infra.messaging.PaymentDebited.class);

        assertThat(event.paymentId()).isEqualTo(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"));
        assertThat(event.amount()).isEqualTo(100);
        assertThat(event.debitedAt()).isEqualTo(Instant.parse("2026-09-24T22:00:00Z"));
    }

    @Test
    void coordinatorReadsTheConfirmedExampleOfTheContract() throws IOException {
        var event = json.readValue(example(CONFIRMED_SCHEMA), co.inter.piggies.coordinator.infra.messaging.PaymentConfirmed.class);

        assertThat(event.paymentId()).isEqualTo(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"));
        assertThat(event.creditedAt()).isEqualTo(Instant.parse("2026-09-24T22:00:01Z"));
    }

    @Test
    void schemaRejectsFractionalAmountAndExtraFields() throws IOException {
        String example = example(DEBITED_SCHEMA);

        assertThat(errors(DEBITED_SCHEMA, example.replace("\"amount\":100", "\"amount\":10.5"))).isNotEmpty();
        assertThat(errors(DEBITED_SCHEMA, example.replace("{", "{\"extra\":true,"))).isNotEmpty();
        assertThat(errors(DEBITED_SCHEMA, example.replace("\"eventVersion\":1", "\"eventVersion\":2"))).isNotEmpty();
    }

    private List<Error> errors(String schemaFile, String event) throws IOException {
        Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(Files.readString(EVENTS.resolve(schemaFile)), InputFormat.JSON);
        return schema.validate(event, InputFormat.JSON);
    }

    /**
     * Primeiro item de {@code examples} do schema, compactado para facilitar as substituições nos testes negativos.
     */
    @SuppressWarnings("unchecked")
    private String example(String schemaFile) throws IOException {
        var schema = json.readValue(Files.readString(EVENTS.resolve(schemaFile)), java.util.Map.class);
        Object first = ((List<Object>) schema.get("examples")).getFirst();
        return json.writeValueAsString(first);
    }
}
