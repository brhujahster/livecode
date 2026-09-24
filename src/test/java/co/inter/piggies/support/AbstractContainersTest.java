package co.inter.piggies.support;


import io.micronaut.test.support.TestPropertyProvider;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractContainersTest implements TestPropertyProvider {

    protected static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
    .withUsername("livecode")
    .withPassword("livecode")
    .withDatabaseName("livecode");


    protected static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    static {
        POSTGRESQL.start();
        KAFKA.start();
    }

    /**
     * {@code create} e não {@code create-drop}: o drop ao fechar o contexto espera locks de transações ainda abertas
     * e pode travar a suíte. Os tópicos têm sufixo por contexto para que eventos que sobraram de uma classe de
     * teste não sejam consumidos pela seguinte.
     */
    @Override
    public Map<String, String> getProperties() {
        String suffix = UUID.randomUUID().toString();
        return Map.of(
                "datasources.default.url", POSTGRESQL.getJdbcUrl(),
                "datasources.default.username", POSTGRESQL.getUsername(),
                "datasources.default.password", POSTGRESQL.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "datasources.default.dialect", "POSTGRES",
                "jpa.default.properties.hibernate.hbm2ddl.auto", "create",
                "jpa.default.properties.hibernate.hbm2ddl.create_namespaces", "true",
                "kafka.bootstrap.servers", KAFKA.getBootstrapServers(),
                "spp.topics.payment-debited", "spp.payment.debited." + suffix,
                "spp.topics.payment-confirmed", "spp.payment.confirmed." + suffix
        );
    }
}
