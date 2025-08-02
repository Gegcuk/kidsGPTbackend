package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.dto.chat.Tone;
import uk.gegc.kidsgptbackend.dto.story.*;
import uk.gegc.kidsgptbackend.model.story.StoryStatus;
import uk.gegc.kidsgptbackend.service.story.StoryService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class StoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoryService storyService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        Mockito.reset(storyService);
    }

    @Test
    void startStory_ShouldReturnStartStoryResponse_WhenValidRequest() throws Exception {
        // Given
        StartStoryRequest request = new StartStoryRequest(
                "The Magic Adventure",
                "A brave little hero starts an amazing journey"
        );
        
        StartStoryResponse expectedResponse = new StartStoryResponse(
                UUID.randomUUID(),
                "The Magic Adventure",
                "What an exciting story! Let's create this magical adventure together. What should happen first in your story?",
                "gpt-4o-mini",
                150L,
                45,
                LocalDateTime.now()
        );

        when(storyService.startStory(any(StartStoryRequest.class), any(Principal.class)))
                .thenReturn(expectedResponse);

        User principal = new User(
                "testuser",
                "password",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // When & Then
        mockMvc.perform(post("/api/v1/stories/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.storyId").exists())
                .andExpect(jsonPath("$.title").value(expectedResponse.title()))
                .andExpect(jsonPath("$.encouragingMessage").value(expectedResponse.encouragingMessage()))
                .andExpect(jsonPath("$.model").value(expectedResponse.model()));

        verify(storyService).startStory(any(StartStoryRequest.class), any(Principal.class));
    }

    @Test
    void continueStory_ShouldReturnContinueStoryResponse_WhenValidRequest() throws Exception {
        // Given
        UUID storyId = UUID.randomUUID();
        
        // Mock conversation context
        java.util.List<StoryMessageDto> context = java.util.List.of(
                new StoryMessageDto(UUID.randomUUID(), "USER", "I want to start a story", LocalDateTime.now()),
                new StoryMessageDto(UUID.randomUUID(), "ASSISTANT", "Great! Let's begin.", LocalDateTime.now())
        );
        
        ContinueStoryRequest request = new ContinueStoryRequest(
                storyId,
                "The hero found a mysterious door in the forest.",
                Tone.FRIENDLY,
                context
        );
        
        ContinueStoryResponse expectedResponse = new ContinueStoryResponse(
                storyId,
                "How exciting! What do you think is behind the mysterious door? Does the hero decide to open it?",
                "gpt-4o-mini",
                120L,
                35
        );

        when(storyService.continueStory(any(ContinueStoryRequest.class), any(Principal.class)))
                .thenReturn(expectedResponse);

        User principal = new User(
                "testuser",
                "password",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // When & Then
        mockMvc.perform(post("/api/v1/stories/continue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.storyId").value(storyId.toString()))
                .andExpect(jsonPath("$.reply").value(expectedResponse.reply()))
                .andExpect(jsonPath("$.model").value(expectedResponse.model()));

        verify(storyService).continueStory(any(ContinueStoryRequest.class), any(Principal.class));
    }

    @Test
    void getStory_ShouldReturnStoryDto_WhenValidRequest() throws Exception {
        // Given
        UUID storyId = UUID.randomUUID();
        StoryDto expectedStory = new StoryDto(
                storyId,
                "The Magic Adventure",
                StoryStatus.IN_PROGRESS,
                Collections.emptyList(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(storyService.getStory(eq(storyId), any(Principal.class)))
                .thenReturn(expectedStory);

        User principal = new User(
                "testuser",
                "password",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // When & Then
        mockMvc.perform(get("/api/v1/stories/" + storyId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(storyId.toString()))
                .andExpect(jsonPath("$.title").value(expectedStory.title()))
                .andExpect(jsonPath("$.status").value(expectedStory.status().toString()));

        verify(storyService).getStory(eq(storyId), any(Principal.class));
    }

    @Test
    void getStories_ShouldReturnPageOfStories_WhenValidRequest() throws Exception {
        // Given
        StoryListDto storyListDto = new StoryListDto(
                UUID.randomUUID(),
                "The Magic Adventure",
                StoryStatus.IN_PROGRESS,
                4,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        Page<StoryListDto> expectedPage = new PageImpl<>(Collections.singletonList(storyListDto));

        when(storyService.getStoriesByUser(any(Principal.class), any()))
                .thenReturn(expectedPage);

        User principal = new User(
                "testuser",
                "password",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // When & Then
        mockMvc.perform(get("/api/v1/stories"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value(storyListDto.title()));

        verify(storyService).getStoriesByUser(any(Principal.class), any());
    }

    @Test
    void startStory_ShouldReturnUnauthorized_WhenNotAuthenticated() throws Exception {
        // Given
        StartStoryRequest request = new StartStoryRequest(
                "Test Story",
                null
        );

        // When & Then
        mockMvc.perform(post("/api/v1/stories/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(storyService, never()).startStory(any(), any());
    }
} 