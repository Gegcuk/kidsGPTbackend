package uk.gegc.kidsgptbackend.features.story.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import uk.gegc.kidsgptbackend.features.story.api.dto.*;
import uk.gegc.kidsgptbackend.features.story.application.StoryService;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
@Tag(name = "Stories", description = "AI-assisted story generation and retrieval")
public class StoryController {

    private final StoryService storyService;

    @Operation(summary = "Start a new story", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/start")
    public ResponseEntity<StartStoryResponse> startStory(
            @Valid @RequestBody StartStoryRequest request,
            @AuthenticationPrincipal User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Principal p = principal::getUsername;
        StartStoryResponse response = storyService.startStory(request, p);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Continue an existing story", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/continue")
    public ResponseEntity<ContinueStoryResponse> continueStory(
            @Valid @RequestBody ContinueStoryRequest request,
            @AuthenticationPrincipal User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Principal p = principal::getUsername;
        ContinueStoryResponse response = storyService.continueStory(request, p);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get a single story by ID", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{storyId}")
    public ResponseEntity<StoryDto> getStory(
            @PathVariable UUID storyId,
            @AuthenticationPrincipal User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Principal p = principal::getUsername;
        StoryDto story = storyService.getStory(storyId, p);
        return ResponseEntity.ok(story);
    }

    @Operation(summary = "List stories for the current user", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<Page<StoryListDto>> getStories(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Principal p = principal::getUsername;
        Page<StoryListDto> stories = storyService.getStoriesByUser(p, pageable);
        return ResponseEntity.ok(stories);
    }
}
