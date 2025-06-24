package uk.gegc.kidsgptbackend.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileDto;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileUpdateRequest;
import uk.gegc.kidsgptbackend.service.family.KidProfileService;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    @Autowired
    private KidProfileService kidProfileService;

    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChildProfileDto> updateProfile(@Valid @RequestBody ChildProfileUpdateRequest request) {
        ChildProfileDto updated = kidProfileService.updateCurrentChildProfile(request);
        return ResponseEntity.ok(updated);
    }
} 