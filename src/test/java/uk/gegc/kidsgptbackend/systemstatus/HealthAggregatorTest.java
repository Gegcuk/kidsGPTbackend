package uk.gegc.kidsgptbackend.systemstatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthAggregatorTest {

    private DbHealthIndicator       dbHealthIndicator;
    private Environment env;
    private SystemStatusService      service;

    @BeforeEach
    void setUp() {
        dbHealthIndicator = mock(DbHealthIndicator.class);
        HealthContributorRegistry healthRegistry = mock(HealthContributorRegistry.class);
        env             = mock(Environment.class);

        service = new SystemStatusService(healthRegistry, env);
        when(healthRegistry.getContributor("db")).thenReturn(dbHealthIndicator);
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("sk-test");        ReflectionTestUtils.setField(service, "commit", "abc");
        ReflectionTestUtils.setField(service, "buildTag", "test-tag");
    }

    @Test
    void dbUpMeansOverallUp() {
        when(dbHealthIndicator.health()).thenReturn(Health.up().build());
        SystemStatusDto dto = service.getStatus();
        assertEquals("UP", dto.getOverall());
        assertEquals("UP", dto.getComponents().get("db"));
    }

    @Test
    void dbDownMeansOverallDown() {
        when(dbHealthIndicator.health()).thenReturn(Health.down().build());
        SystemStatusDto dto = service.getStatus();
        assertEquals("DOWN", dto.getOverall());
        assertEquals("DOWN", dto.getComponents().get("db"));
    }
}