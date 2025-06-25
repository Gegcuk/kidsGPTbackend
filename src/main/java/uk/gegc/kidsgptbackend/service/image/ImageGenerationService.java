package uk.gegc.kidsgptbackend.service.image;

import uk.gegc.kidsgptbackend.dto.image.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.dto.image.ImageGenerationResponse;

import java.security.Principal;

public interface ImageGenerationService {
    ImageGenerationResponse generateImage(ImageGenerationRequest request, Principal principal);
} 