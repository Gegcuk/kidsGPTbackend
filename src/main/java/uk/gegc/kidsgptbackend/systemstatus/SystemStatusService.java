package uk.gegc.kidsgptbackend.systemstatus;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.*;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemStatusService {

    private final HealthContributorRegistry healthRegistry;   // Actuator SPI
    private final Environment               env;              // resolved properties

    private final long startTime = System.currentTimeMillis();

    @Value("${git.commit.id.abbrev:unknown}")
    private String commit;

    @Value("${git.build.version:unknown}")
    private String buildTag;

    public SystemStatusDto getStatus() {

        /* ---------- DB health ---------- */
        boolean dbUp = isDbUp();

        /* ---------- OpenAI key ---------- */
        String   key          = env.getProperty("spring.ai.openai.api-key", "");
        boolean  keyPresent   = StringUtils.hasText(key);
        boolean  keyLooksGood = keyPresent && key.startsWith("sk-");

        /* ---------- assemble DTO ---------- */
        Map<String, String> components = new HashMap<>();
        components.put("db",        dbUp        ? "UP"            : "DOWN");
        components.put("openaiKey", keyPresent  ? (keyLooksGood   ? "PRESENT"
                : "INVALID_FORMAT")
                : "MISSING");

        SystemStatusDto.VersionInfo version = new SystemStatusDto.VersionInfo();
        version.setCommit(commit);
        version.setBuildTag(buildTag);

        SystemStatusDto dto = new SystemStatusDto();
        dto.setOverall(dbUp && keyLooksGood ? "UP" : "DOWN");
        dto.setApp("UP");
        dto.setUptimeSeconds((System.currentTimeMillis() - startTime) / 1000);
        dto.setVersion(version);
        dto.setTimestamp(Instant.now());
        dto.setComponents(components);

        return dto;
    }

    /* ---------- helper ---------- */

    private boolean isDbUp() {
        HealthContributor contributor = healthRegistry.getContributor("db");           // :contentReference[oaicite:1]{index=1}
        if (contributor == null) {
            return false; // no datasource => consider DOWN
        }
        if (contributor instanceof HealthIndicator hi) {                               // simple case
            return hi.health().getStatus() == Status.UP;
        }
        if (contributor instanceof CompositeHealthContributor composite) {             // multi-DS case
            for (Iterator<NamedContributor<HealthContributor>> it = composite.iterator(); it.hasNext(); ) {
                HealthContributor hc = it.next().getContributor();
                if (hc instanceof HealthIndicator h && h.health().getStatus() != Status.UP) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}