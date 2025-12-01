package uk.gegc.kidsgptbackend.features.consent.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiateRequest;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationInitiationResult;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationStatusResponse;
import uk.gegc.kidsgptbackend.features.consent.api.dto.VerificationVerifyRequest;
import uk.gegc.kidsgptbackend.features.consent.application.ParentVerificationService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verification")
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Parent Verification", description = "Parent verification endpoints for consent management")
@SecurityRequirement(name = "bearerAuth")
public class VerificationController {

    private final ParentVerificationService parentVerificationService;

    /**
     * Initiate parent verification process
     * POST /api/v1/verification/initiate
     */
    @Operation(
        summary = "Initiate parent verification",
        description = "Starts verification process via EMAIL or SMS. Returns 201 for new verification, 200 for existing pending verification."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Verification created successfully",
            content = @Content(schema = @Schema(implementation = VerificationStatusResponse.class))),
        @ApiResponse(responseCode = "200", description = "Existing pending verification returned",
            content = @Content(schema = @Schema(implementation = VerificationStatusResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data",
            content = @Content(examples = {
                @ExampleObject(
                    name = "Invalid UUID",
                    value = """
                    {
                      "type":"/errors/validation-failed",
                      "title":"Validation Failed",
                      "status":400,
                      "detail":"parentId: Parent ID is required",
                      "instance":"/api/v1/verification/initiate",
                      "timestamp":"2025-01-01T12:00:00Z",
                      "errors":["parentId: Parent ID is required"]
                    }
                    """
                ),
                @ExampleObject(
                    name = "Invalid email",
                    value = """
                    {
                      "type":"/errors/validation-failed",
                      "title":"Validation Failed",
                      "status":400,
                      "detail":"contactInfo: contactInfo must be a valid email address when verificationMethod=EMAIL",
                      "instance":"/api/v1/verification/initiate",
                      "timestamp":"2025-01-01T12:00:00Z",
                      "errors":["contactInfo: contactInfo must be a valid email address when verificationMethod=EMAIL"]
                    }
                    """
                )
            })),
        @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    @PostMapping(
        value = "/initiate",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<VerificationStatusResponse> initiateVerification(
            @Valid @RequestBody VerificationInitiateRequest request) {

        log.info("Initiating verification: parent={}, method={}", request.parentId(), request.verificationMethod());

        VerificationInitiationResult result = parentVerificationService.initiateVerification(request);
        VerificationStatusResponse response = result.verificationStatus();

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/verification/status/{id}")
                .buildAndExpand(response.verificationId())
                .toUri();
        
        if (result.newlyCreated()) {
            return ResponseEntity
                    .created(location)
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .header("X-Verification-Id", response.verificationId().toString())
                    .body(response);
        } else {
            return ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore().mustRevalidate())
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .header("X-Verification-Id", response.verificationId().toString())
                    .body(response);
        }
    }

    /**
     * Verify parent with verification code
     * POST /api/v1/verification/verify
     */
    @Operation(
        summary = "Verify parent with code",
        description = "Verifies parent using the verification code sent via email/SMS"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verification successful",
            content = @Content(schema = @Schema(implementation = VerificationStatusResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid verification code format"),
        @ApiResponse(responseCode = "409", description = "Already verified"),
        @ApiResponse(responseCode = "410", description = "Verification expired")
    })
    @PostMapping(
        value = "/verify",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<VerificationStatusResponse> verifyParent(
            @Valid @RequestBody VerificationVerifyRequest request) {

        log.info("Verifying: verificationId={}", request.verificationId());

        VerificationStatusResponse response = parentVerificationService.verifyParent(request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header("X-Verification-Id", response.verificationId().toString())
                .body(response);
    }

    /**
     * Get verification status
     * GET /api/v1/verification/status/{verificationId}
     */
    @Operation(
        summary = "Get verification status",
        description = "Retrieves the current status of a verification process"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verification status retrieved",
            content = @Content(schema = @Schema(implementation = VerificationStatusResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid verification ID format"),
        @ApiResponse(responseCode = "404", description = "Verification not found")
    })
    @GetMapping(
        value = "/status/{verificationId}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<VerificationStatusResponse> getVerificationStatus(
            @Parameter(example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable @Schema(format = "uuid") UUID verificationId) {

        log.info("Retrieving status: verificationId={}", verificationId);

        VerificationStatusResponse response = parentVerificationService.getVerificationStatus(verificationId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(response);
    }
}

