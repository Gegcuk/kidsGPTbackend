package uk.gegc.kidsgptbackend.service.family;

import uk.gegc.kidsgptbackend.dto.user.ChildProfileDto;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileUpdateRequest;

public interface KidProfileService {
    ChildProfileDto updateCurrentChildProfile(ChildProfileUpdateRequest request);
} 