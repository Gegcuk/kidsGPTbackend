package uk.gegc.kidsgptbackend.service.family;

import uk.gegc.kidsgptbackend.dto.user.ChildProfileDto;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.dto.user.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.dto.user.ParentUpdateKidRequest;

import java.util.UUID;

public interface KidProfileService {
    // New methods
    ChildProfileDto updateKidSelfProfile(KidSelfUpdateRequest request);
    ChildProfileDto updateKidProfileByParent(UUID kidId, ParentUpdateKidRequest request);
    
    // Legacy method - keep for backward compatibility with existing tests
    @Deprecated
    ChildProfileDto updateCurrentChildProfile(ChildProfileUpdateRequest request);
} 