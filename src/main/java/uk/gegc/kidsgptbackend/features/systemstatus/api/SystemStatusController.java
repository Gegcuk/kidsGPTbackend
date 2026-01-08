package uk.gegc.kidsgptbackend.features.systemstatus.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gegc.kidsgptbackend.features.systemstatus.api.dto.SystemStatusDto;
import uk.gegc.kidsgptbackend.features.systemstatus.application.impl.SystemStatusServiceImpl;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Health and system status checks")
public class SystemStatusController {

    private final SystemStatusServiceImpl service;

    public SystemStatusController(SystemStatusServiceImpl service) {
        this.service = service;
    }

    @Operation(summary = "Health/status probe")
    @GetMapping("/status")
    public SystemStatusDto status() {
        return service.getStatus();
    }
}
