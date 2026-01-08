package uk.gegc.kidsgptbackend.features.image.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import uk.gegc.kidsgptbackend.features.image.api.dto.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.features.image.api.dto.ImageGenerationResponse;
import uk.gegc.kidsgptbackend.features.image.application.ImageGenerationService;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Images", description = "AI image generation for kids")
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    @Operation(
            summary = "Generate an image from a prompt",
            description = "Requires active subscription. Base allowance: 2 images per billing period. Additional image packs add credits. Returns 400 when subscription is missing or quota is exceeded.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Image generated",
                            content = @Content(schema = @Schema(implementation = ImageGenerationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation failed, subscription missing, or quota exceeded",
                            content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    @PostMapping("/generate-image")
    public ResponseEntity<ImageGenerationResponse> generateImage(
            @Valid @RequestBody ImageGenerationRequest request,
            @AuthenticationPrincipal User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            log.info("Image generation request from user: {}, description: {}", 
                    principal.getUsername(), request.description());
            
            Principal p = principal::getUsername;
            ImageGenerationResponse response = imageGenerationService.generateImage(request, p);
            
            log.info("Image generated successfully for user: {}, latency: {}ms", 
                    principal.getUsername(), response.latencyMs());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Invalid image generation request from user {}: {}", 
                    principal.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error generating image for user {}: {}", 
                    principal.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
