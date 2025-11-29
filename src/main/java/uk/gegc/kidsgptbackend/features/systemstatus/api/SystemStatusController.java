package uk.gegc.kidsgptbackend.features.systemstatus.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gegc.kidsgptbackend.features.systemstatus.api.dto.SystemStatusDto;
import uk.gegc.kidsgptbackend.features.systemstatus.application.impl.SystemStatusServiceImpl;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final SystemStatusServiceImpl service;

    public SystemStatusController(SystemStatusServiceImpl service) {
        this.service = service;
    }

    @GetMapping("/status")
    public SystemStatusDto status() {
        return service.getStatus();
    }
}
