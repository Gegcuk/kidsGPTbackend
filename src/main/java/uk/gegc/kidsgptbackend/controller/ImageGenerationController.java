package uk.gegc.kidsgptbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationResponse;
import uk.gegc.kidsgptbackend.service.image.ImageGenerationService;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

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
        } catch (IllegalArgumentException e) {
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