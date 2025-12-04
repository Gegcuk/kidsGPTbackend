package uk.gegc.kidsgptbackend.features.family.api;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import uk.gegc.kidsgptbackend.features.user.api.dto.ChildProfileDto;
import uk.gegc.kidsgptbackend.features.user.api.dto.KidSelfUpdateRequest;
import uk.gegc.kidsgptbackend.features.user.api.dto.ParentUpdateKidRequest;
import uk.gegc.kidsgptbackend.features.family.application.KidProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profiles", description = "Kid profile updates by kid or parent")
public class ProfileController {
    @Autowired
    private KidProfileService kidProfileService;

    @Operation(summary = "Kid updates their own profile", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping
    @PreAuthorize("hasRole('CHILD')")
    public ResponseEntity<ChildProfileDto> updateOwnProfile(@Valid @RequestBody KidSelfUpdateRequest request) {
        ChildProfileDto updated = kidProfileService.updateKidSelfProfile(request);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Parent updates a kid profile", security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/kid/{kidId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ChildProfileDto> updateKidProfile(
            @PathVariable UUID kidId,
            @Valid @RequestBody ParentUpdateKidRequest request) {
        ChildProfileDto updated = kidProfileService.updateKidProfileByParent(kidId, request);
        return ResponseEntity.ok(updated);
    }
}
