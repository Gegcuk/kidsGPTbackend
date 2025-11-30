package uk.gegc.kidsgptbackend.features.family.application;

import uk.gegc.kidsgptbackend.features.user.api.dto.ChildProfileDto;
import uk.gegc.kidsgptbackend.features.user.api.dto.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.ParentUpdateKidRequest;

import java.util.UUID;

public interface KidProfileService {
    // New methods
    ChildProfileDto updateKidSelfProfile(KidSelfUpdateRequest request);
    ChildProfileDto updateKidProfileByParent(UUID kidId, ParentUpdateKidRequest request);
    
    // Legacy method - keep for backward compatibility with existing tests
    @Deprecated
    ChildProfileDto updateCurrentChildProfile(ChildProfileUpdateRequest request);
} 