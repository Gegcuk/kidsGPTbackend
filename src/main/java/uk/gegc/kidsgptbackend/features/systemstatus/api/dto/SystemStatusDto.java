package uk.gegc.kidsgptbackend.features.systemstatus.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Schema(description = "System health/status payload")
@Data
public class SystemStatusDto {
    @Schema(description = "Overall status string, e.g., OK/DEGRADED")
    private String overall;
    @Schema(description = "Application status")
    private String app;
    @Schema(description = "Uptime in seconds")
    private long uptimeSeconds;
    @Schema(description = "Version metadata")
    private VersionInfo version;
    @Schema(description = "Timestamp of the status check")
    private Instant timestamp;
    @Schema(description = "Status of dependent components")
    private Map<String, String> components;

    @Schema(description = "Build/version details")
    @Data
    public static class VersionInfo {
        @Schema(description = "Git commit hash")
        private String commit;
        @Schema(description = "Build tag/version label")
        private String buildTag;
    }
}
