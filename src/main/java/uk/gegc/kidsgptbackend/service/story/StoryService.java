package uk.gegc.kidsgptbackend.service.story;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gegc.kidsgptbackend.dto.story.*;

import java.security.Principal;
import java.util.UUID;

public interface StoryService {
    StartStoryResponse startStory(StartStoryRequest request, Principal principal);
    ContinueStoryResponse continueStory(ContinueStoryRequest request, Principal principal);
    StoryDto getStory(UUID storyId, Principal principal);
    Page<StoryListDto> getStoriesByUser(Principal principal, Pageable pageable);
} 