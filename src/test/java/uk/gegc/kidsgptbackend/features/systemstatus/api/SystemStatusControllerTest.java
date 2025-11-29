package uk.gegc.kidsgptbackend.features.systemstatus.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gegc.kidsgptbackend.features.systemstatus.api.dto.SystemStatusDto;
import uk.gegc.kidsgptbackend.features.systemstatus.application.impl.SystemStatusServiceImpl;
import uk.gegc.kidsgptbackend.test.BaseUnitTest;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SystemStatusControllerTest extends BaseUnitTest {

    @Mock
    private SystemStatusServiceImpl systemStatusService;

    @InjectMocks
    private SystemStatusController systemStatusController;

    private SystemStatusDto mockStatusDto;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        mockStatusDto = new SystemStatusDto();
        mockStatusDto.setOverall("UP");
        mockStatusDto.setApp("kidsGPT-backend");
        mockStatusDto.setUptimeSeconds(3600L);
        mockStatusDto.setTimestamp(Instant.now());

        SystemStatusDto.VersionInfo versionInfo = new SystemStatusDto.VersionInfo();
        versionInfo.setCommit("test-commit");
        versionInfo.setBuildTag("test-tag");
        mockStatusDto.setVersion(versionInfo);

        mockStatusDto.setComponents(Map.of("db", "UP", "disk", "UP"));
    }

    @Test
    @DisplayName("status: returns system status")
    void status_returnsSystemStatus() {
        when(systemStatusService.getStatus()).thenReturn(mockStatusDto);

        SystemStatusDto response = systemStatusController.status();

        assertThat(response).isNotNull();
        assertThat(response.getOverall()).isEqualTo("UP");
        assertThat(response.getApp()).isEqualTo("kidsGPT-backend");
        assertThat(response.getUptimeSeconds()).isEqualTo(3600L);
        assertThat(response.getVersion().getCommit()).isEqualTo("test-commit");
        assertThat(response.getVersion().getBuildTag()).isEqualTo("test-tag");
        assertThat(response.getComponents()).containsEntry("db", "UP");
        assertThat(response.getComponents()).containsEntry("disk", "UP");
    }

    @Test
    @DisplayName("status: returns status with DOWN overall")
    void status_returnsDownStatus() {
        SystemStatusDto downStatus = new SystemStatusDto();
        downStatus.setOverall("DOWN");
        downStatus.setApp("kidsGPT-backend");
        downStatus.setUptimeSeconds(3600L);
        downStatus.setTimestamp(Instant.now());

        SystemStatusDto.VersionInfo versionInfo = new SystemStatusDto.VersionInfo();
        versionInfo.setCommit("test-commit");
        versionInfo.setBuildTag("test-tag");
        downStatus.setVersion(versionInfo);

        downStatus.setComponents(Map.of("db", "DOWN", "disk", "UP"));

        when(systemStatusService.getStatus()).thenReturn(downStatus);

        SystemStatusDto response = systemStatusController.status();

        assertThat(response).isNotNull();
        assertThat(response.getOverall()).isEqualTo("DOWN");
        assertThat(response.getComponents()).containsEntry("db", "DOWN");
        assertThat(response.getComponents()).containsEntry("disk", "UP");
    }

    @Test
    @DisplayName("status: returns status with empty components")
    void status_returnsStatusWithEmptyComponents() {
        SystemStatusDto emptyComponentsStatus = new SystemStatusDto();
        emptyComponentsStatus.setOverall("UP");
        emptyComponentsStatus.setApp("kidsGPT-backend");
        emptyComponentsStatus.setUptimeSeconds(3600L);
        emptyComponentsStatus.setTimestamp(Instant.now());

        SystemStatusDto.VersionInfo versionInfo = new SystemStatusDto.VersionInfo();
        versionInfo.setCommit("test-commit");
        versionInfo.setBuildTag("test-tag");
        emptyComponentsStatus.setVersion(versionInfo);

        emptyComponentsStatus.setComponents(Map.of());

        when(systemStatusService.getStatus()).thenReturn(emptyComponentsStatus);

        SystemStatusDto response = systemStatusController.status();

        assertThat(response).isNotNull();
        assertThat(response.getComponents()).isEmpty();
    }

    @Test
    @DisplayName("status: returns status with null values")
    void status_returnsStatusWithNullValues() {
        SystemStatusDto nullValuesStatus = new SystemStatusDto();
        nullValuesStatus.setOverall("UP");
        nullValuesStatus.setApp(null);
        nullValuesStatus.setUptimeSeconds(0L);
        nullValuesStatus.setTimestamp(null);
        nullValuesStatus.setVersion(null);
        nullValuesStatus.setComponents(Map.of("db", "UP"));

        when(systemStatusService.getStatus()).thenReturn(nullValuesStatus);

        SystemStatusDto response = systemStatusController.status();

        assertThat(response).isNotNull();
        assertThat(response.getApp()).isNull();
        assertThat(response.getVersion()).isNull();
        assertThat(response.getTimestamp()).isNull();
    }

    @Test
    @DisplayName("status: returns status with multiple components")
    void status_returnsStatusWithMultipleComponents() {
        SystemStatusDto multiComponentStatus = new SystemStatusDto();
        multiComponentStatus.setOverall("UP");
        multiComponentStatus.setApp("kidsGPT-backend");
        multiComponentStatus.setUptimeSeconds(3600L);
        multiComponentStatus.setTimestamp(Instant.now());

        SystemStatusDto.VersionInfo versionInfo = new SystemStatusDto.VersionInfo();
        versionInfo.setCommit("test-commit");
        versionInfo.setBuildTag("test-tag");
        multiComponentStatus.setVersion(versionInfo);

        multiComponentStatus.setComponents(Map.of(
                "db", "UP",
                "disk", "UP",
                "memory", "UP",
                "cpu", "UP"
        ));

        when(systemStatusService.getStatus()).thenReturn(multiComponentStatus);

        SystemStatusDto response = systemStatusController.status();

        assertThat(response).isNotNull();
        assertThat(response.getComponents()).hasSize(4);
        assertThat(response.getComponents()).containsEntry("db", "UP");
        assertThat(response.getComponents()).containsEntry("disk", "UP");
        assertThat(response.getComponents()).containsEntry("memory", "UP");
        assertThat(response.getComponents()).containsEntry("cpu", "UP");
    }
} 