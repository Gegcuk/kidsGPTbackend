package uk.gegc.kidsgptbackend.systemstatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.*;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemStatusServiceTest {

    @Mock
    private HealthContributorRegistry healthRegistry;

    @Mock
    private Environment env;

    @InjectMocks
    private SystemStatusService systemStatusService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(systemStatusService, "commit", "abc123");
        ReflectionTestUtils.setField(systemStatusService, "buildTag", "v1.0.0");
    }

    @Test
    @DisplayName("getStatus: returns UP when db is up and openai key is valid")
    void getStatus_dbUpAndValidKey_returnsUp() {
        // Mock DB health
        HealthIndicator dbIndicator = mock(HealthIndicator.class);
        Health dbHealth = Health.up().build();
        when(dbIndicator.health()).thenReturn(dbHealth);
        when(healthRegistry.getContributor("db")).thenReturn(dbIndicator);

        // Mock OpenAI key
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("sk-valid-key");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("UP");
        assertThat(result.getApp()).isEqualTo("UP");
        assertThat(result.getUptimeSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(result.getTimestamp()).isBeforeOrEqualTo(Instant.now());
        assertThat(result.getVersion().getCommit()).isEqualTo("abc123");
        assertThat(result.getVersion().getBuildTag()).isEqualTo("v1.0.0");
        
        Map<String, String> components = result.getComponents();
        assertThat(components.get("db")).isEqualTo("UP");
        assertThat(components.get("openaiKey")).isEqualTo("PRESENT");
    }

    @Test
    @DisplayName("getStatus: returns DOWN when db is down")
    void getStatus_dbDown_returnsDown() {
        // Mock DB health as down
        HealthIndicator dbIndicator = mock(HealthIndicator.class);
        Health dbHealth = Health.down().build();
        when(dbIndicator.health()).thenReturn(dbHealth);
        when(healthRegistry.getContributor("db")).thenReturn(dbIndicator);

        // Mock OpenAI key
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("sk-valid-key");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("DOWN");
        assertThat(result.getComponents().get("db")).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("getStatus: returns DOWN when openai key is missing")
    void getStatus_missingOpenAiKey_returnsDown() {
        // Mock DB health as up
        HealthIndicator dbIndicator = mock(HealthIndicator.class);
        Health dbHealth = Health.up().build();
        when(dbIndicator.health()).thenReturn(dbHealth);
        when(healthRegistry.getContributor("db")).thenReturn(dbIndicator);

        // Mock missing OpenAI key
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("DOWN");
        assertThat(result.getComponents().get("openaiKey")).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("getStatus: returns DOWN when openai key has invalid format")
    void getStatus_invalidOpenAiKeyFormat_returnsDown() {
        // Mock DB health as up
        HealthIndicator dbIndicator = mock(HealthIndicator.class);
        Health dbHealth = Health.up().build();
        when(dbIndicator.health()).thenReturn(dbHealth);
        when(healthRegistry.getContributor("db")).thenReturn(dbIndicator);

        // Mock invalid OpenAI key format
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("invalid-key");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("DOWN");
        assertThat(result.getComponents().get("openaiKey")).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("getStatus: handles null db contributor")
    void getStatus_nullDbContributor_returnsDown() {
        when(healthRegistry.getContributor("db")).thenReturn(null);
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("sk-valid-key");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("DOWN");
        assertThat(result.getComponents().get("db")).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("getStatus: handles composite health contributor with all up")
    void getStatus_compositeContributorAllUp_returnsUp() {
        // Mock composite health contributor
        CompositeHealthContributor composite = mock(CompositeHealthContributor.class);
        Iterator<NamedContributor<HealthContributor>> iterator = mock(Iterator.class);
        NamedContributor<HealthContributor> namedContributor = mock(NamedContributor.class);
        HealthIndicator healthIndicator = mock(HealthIndicator.class);
        
        when(healthRegistry.getContributor("db")).thenReturn(composite);
        when(composite.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.next()).thenReturn(namedContributor);
        when(namedContributor.getContributor()).thenReturn(healthIndicator);
        when(healthIndicator.health()).thenReturn(Health.up().build());
        
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("sk-valid-key");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("UP");
        assertThat(result.getComponents().get("db")).isEqualTo("UP");
    }

    @Test
    @DisplayName("getStatus: handles composite health contributor with one down")
    void getStatus_compositeContributorOneDown_returnsDown() {
        // Mock composite health contributor with one down
        CompositeHealthContributor composite = mock(CompositeHealthContributor.class);
        Iterator<NamedContributor<HealthContributor>> iterator = mock(Iterator.class);
        NamedContributor<HealthContributor> namedContributor = mock(NamedContributor.class);
        HealthIndicator healthIndicator = mock(HealthIndicator.class);
        
        when(healthRegistry.getContributor("db")).thenReturn(composite);
        when(composite.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.next()).thenReturn(namedContributor);
        when(namedContributor.getContributor()).thenReturn(healthIndicator);
        when(healthIndicator.health()).thenReturn(Health.down().build());
        
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("sk-valid-key");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("DOWN");
        assertThat(result.getComponents().get("db")).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("getStatus: handles unknown contributor type")
    void getStatus_unknownContributorType_returnsDown() {
        HealthContributor unknownContributor = mock(HealthContributor.class);
        when(healthRegistry.getContributor("db")).thenReturn(unknownContributor);
        when(env.getProperty("spring.ai.openai.api-key", "")).thenReturn("sk-valid-key");

        SystemStatusDto result = systemStatusService.getStatus();

        assertThat(result.getOverall()).isEqualTo("DOWN");
        assertThat(result.getComponents().get("db")).isEqualTo("DOWN");
    }
} 