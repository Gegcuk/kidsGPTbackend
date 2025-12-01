package uk.gegc.kidsgptbackend.features.story.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gegc.kidsgptbackend.features.story.api.dto.*;

import java.security.Principal;
import java.util.UUID;

public interface StoryService {
    StartStoryResponse startStory(StartStoryRequest request, Principal principal);
    ContinueStoryResponse continueStory(ContinueStoryRequest request, Principal principal);
    StoryDto getStory(UUID storyId, Principal principal);
    Page<StoryListDto> getStoriesByUser(Principal principal, Pageable pageable);
}

