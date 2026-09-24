package co.inter.piggies;

import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest; 

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public class PostgresContainerTest extends AbstractContainersTest {

   @Inject
   DataSource dataSource;

   @Test
   void shouldConnectToPostgresContainer() throws Exception {
    assertThat(POSTGRESQL.isRunning()).isTrue();

    try(var connection = dataSource.getConnection();
    var statement = connection.createStatement();
    var resultSet = statement.executeQuery("SELECT version()")) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString(1)).contains("PostgreSQL 16");
    }
}
}