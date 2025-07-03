package uk.gegc.kidsgptbackend.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileDto;
import uk.gegc.kidsgptbackend.dto.user.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.dto.user.ParentUpdateKidRequest;
import uk.gegc.kidsgptbackend.service.family.KidProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    @Autowired
    private KidProfileService kidProfileService;

    @PatchMapping
    @PreAuthorize("hasRole('CHILD')")
    public ResponseEntity<ChildProfileDto> updateOwnProfile(@Valid @RequestBody KidSelfUpdateRequest request) {
        ChildProfileDto updated = kidProfileService.updateKidSelfProfile(request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/kid/{kidId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ChildProfileDto> updateKidProfile(
            @PathVariable UUID kidId,
            @Valid @RequestBody ParentUpdateKidRequest request) {
        ChildProfileDto updated = kidProfileService.updateKidProfileByParent(kidId, request);
        return ResponseEntity.ok(updated);
    }
} 