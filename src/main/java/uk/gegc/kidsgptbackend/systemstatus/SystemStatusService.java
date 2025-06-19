package uk.gegc.kidsgptbackend.systemstatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemStatusService {

    private final DbHealthIndicator dbHealthIndicator;
    private final long startTime = System.currentTimeMillis();

    @Value("${git.commit.id.abbrev:unknown}")
    private String commit;

    @Value("${git.build.version:unknown}")
    private String buildTag;

    public SystemStatusService(DbHealthIndicator dbHealthIndicator) {
        this.dbHealthIndicator = dbHealthIndicator;
    }

    public SystemStatusDto getStatus() {
        boolean dbUp = dbHealthIndicator.health().getStatus() == Status.UP;
        SystemStatusDto dto = new SystemStatusDto();
        dto.setOverall(dbUp ? "UP" : "DOWN");
        dto.setApp("UP");
        dto.setUptimeSeconds((System.currentTimeMillis() - startTime) / 1000);
        SystemStatusDto.VersionInfo version = new SystemStatusDto.VersionInfo();
        version.setCommit(commit);
        version.setBuildTag(buildTag);
        dto.setVersion(version);
        dto.setTimestamp(Instant.now());
        Map<String, String> components = new HashMap<>();
        components.put("db", dbUp ? "UP" : "DOWN");
        dto.setComponents(components);
        return dto;
    }
}
