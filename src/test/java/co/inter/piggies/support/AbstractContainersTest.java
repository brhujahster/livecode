package co.inter.piggies.support;


import io.micronaut.test.support.TestPropertyProvider;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

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

      @Override
    public Map<String, String> getProperties() {
        return Map.of(
                "datasources.default.url", POSTGRESQL.getJdbcUrl(),
                "datasources.default.username", POSTGRESQL.getUsername(),
                "datasources.default.password", POSTGRESQL.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "datasources.default.dialect", "POSTGRES",
                "jpa.default.properties.hibernate.hbm2ddl.auto", "create-drop",
                "kafka.bootstrap.servers", KAFKA.getBootstrapServers()
        );
    }
}
