package uk.gegc.kidsgptbackend.features.systemstatus.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Diagnostics", description = "Lightweight echo and diagnostics endpoints")
public class EchoController {

    @Operation(summary = "Echo back a message (diagnostics)")
    @GetMapping("/echo")
    public String echo(@RequestParam(value = "msg", defaultValue = "Hello") String message) {
        return message + "1";
    }

}
