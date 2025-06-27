package uk.gegc.kidsgptbackend.controller;

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
import uk.gegc.kidsgptbackend.dto.story.*;
import uk.gegc.kidsgptbackend.service.story.StoryService;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

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