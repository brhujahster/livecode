package co.inter.piggies;

import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public class KafkaContainerTest extends AbstractContainersTest {

    @Test
    void shouldConnectToKafkaContainer() throws Exception {
        assertThat(KAFKA.isRunning()).isTrue();

        try(var admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            var nodes = admin.describeCluster().nodes().get(30, TimeUnit.SECONDS);
            assertThat(nodes).hasSize(1);
        }
    }
}