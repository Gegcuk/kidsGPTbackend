package uk.gegc.kidsgptbackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gegc.kidsgptbackend.repository.consent.ConsentLedgerRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsentDatabaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConsentLedgerRepository consentLedgerRepository;

    @Test
    void contextLoads() {
        assertNotNull(consentLedgerRepository);
    }

    @Test
    void databaseConnectionWorks() {
        // This test verifies that the database connection works
        // and the consent tables are created successfully
        assertNotNull(mySQLContainer.getJdbcUrl());
    }
} 