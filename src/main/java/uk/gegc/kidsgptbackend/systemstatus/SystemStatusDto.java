package uk.gegc.kidsgptbackend.systemstatus;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class SystemStatusDto {
    private String overall;
    private String app;
    private long uptimeSeconds;
    private VersionInfo version;
    private Instant timestamp;
    private Map<String, String> components;

    @Data
    public static class VersionInfo {
        private String commit;
        private String buildTag;
    }
}
