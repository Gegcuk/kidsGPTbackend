package uk.gegc.kidsgptbackend.features.image.application;

import uk.gegc.kidsgptbackend.features.image.api.dto.ImageGenerationRequest;
import uk.gegc.kidsgptbackend.features.image.api.dto.ImageGenerationResponse;

import java.security.Principal;

public interface ImageGenerationService {
    ImageGenerationResponse generateImage(ImageGenerationRequest request, Principal principal);
}

