package uk.gegc.kidsgptbackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class KidsGpTbackendApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        // Verify that the Spring context starts without exceptions
    }

    @Test
    void databaseConnectionIsValid() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isValid(1)).isTrue();
        }
    }
}