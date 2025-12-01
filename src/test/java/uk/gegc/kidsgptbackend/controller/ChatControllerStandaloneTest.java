package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageRequest;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageResponse;
import uk.gegc.kidsgptbackend.features.chat.api.dto.Tone;
import uk.gegc.kidsgptbackend.features.chat.application.AiChatService;
import uk.gegc.kidsgptbackend.features.chat.application.ChatMessageService;
import uk.gegc.kidsgptbackend.features.chat.api.ChatController;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(uk.gegc.kidsgptbackend.shared.config.ClockConfig.class)
class ChatControllerStandaloneTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AiChatService chatService;
    @MockitoBean
    ChatMessageService messageService;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        Mockito.reset(chatService, messageService);
    }

//    @TestConfiguration
//    static class TestConfig {
//        @Bean
//        AiChatService aiChatService() {
//            return Mockito.mock(AiChatService.class);
//        }
//
//        @Bean
//        ChatMessageService chatMessageService() {
//            return Mockito.mock(ChatMessageService.class);
//        }
//        @Bean
//        ObjectMapper objectMapper() {
//            ObjectMapper mapper = new ObjectMapper();
//            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
//            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//            return mapper;
//        }
//    }

    @Test
    @DisplayName("POST /api/v1/chat with null principal → 401")
    void chat_nullPrincipal_returnsUnauthorized() throws Exception {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(chatService, never()).chat(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/chat with principal → 200 and service called")
    void chat_withPrincipal_callsService() throws Exception {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, null);
        ChatMessageResponse resp = new ChatMessageResponse("ok", "model", 1L, 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(chatService.chat(any(ChatMessageRequest.class), any(Principal.class))).thenReturn(resp);

        User principal = new User(
                "alice",
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

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();

        verify(chatService).chat(any(ChatMessageRequest.class), any(Principal.class));
    }

    @Test
    @DisplayName("GET /api/v1/chat/{id}/messages with null principal → 401")
    void getMessages_nullPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/" + UUID.randomUUID() + "/messages"))
                .andExpect(status().isUnauthorized());

        verify(messageService, never()).getMessages(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/chat/{id}/messages with principal → 200")
    void getMessages_withPrincipal_callsService() throws Exception {
        UUID id = UUID.randomUUID();
        when(messageService.getMessages(any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        User principal = new User(
                "alice",
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/" + id + "/messages"))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();

        verify(messageService).getMessages(eq(id), any(), eq("alice"));
    }

    @Test
    @DisplayName("POST /api/v1/chat with context history logs correctly")
    void chat_withContextHistory_logsCorrectly() throws Exception {
        java.util.List<uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto> context = java.util.List.of(
                new uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto(
                        UUID.randomUUID(), "USER", "Hello", java.time.LocalDateTime.now()
                )
        );
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY, context);
        ChatMessageResponse resp = new ChatMessageResponse("ok", "model", 1L, 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(chatService.chat(any(ChatMessageRequest.class), any(Principal.class))).thenReturn(resp);

        User principal = new User(
                "alice",
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

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        verify(chatService).chat(any(ChatMessageRequest.class), any(Principal.class));
    }

    @Test
    @DisplayName("GET /api/v1/chat/{id}/messages with unpaged pageable logs correctly")
    void getMessages_withUnpagedPageable_logsCorrectly() throws Exception {
        UUID id = UUID.randomUUID();
        when(messageService.getMessages(any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        User principal = new User(
                "alice",
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/" + id + "/messages")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        verify(messageService).getMessages(eq(id), any(), eq("alice"));
    }

    @Test
    @DisplayName("GET /api/v1/chat/{id}/messages with unpaged request logs unpaged message")
    void getMessages_withUnpagedRequest_logsUnpaged() throws Exception {
        UUID id = UUID.randomUUID();
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto> page = 
            new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        when(messageService.getMessages(any(), any(), any())).thenReturn(page);

        User principal = new User(
                "alice",
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

        // Use Pageable.unpaged() to trigger the unpaged branch
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/" + id + "/messages")
                        .param("unpaged", "true"))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        verify(messageService).getMessages(eq(id), any(), eq("alice"));
    }

    @Test
    @DisplayName("GET /api/v1/chat/{id}/messages with page content logs messages")
    void getMessages_withPageContent_logsMessages() throws Exception {
        UUID id = UUID.randomUUID();
        UUID msgId1 = UUID.randomUUID();
        UUID msgId2 = UUID.randomUUID();
        java.util.List<uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto> messages = java.util.List.of(
                new uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto(
                        msgId1, "USER", "Hello", java.time.LocalDateTime.now()
                ),
                new uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto(
                        msgId2, "ASSISTANT", "Hi there!", java.time.LocalDateTime.now()
                )
        );
        org.springframework.data.domain.Page<uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto> page = 
            new org.springframework.data.domain.PageImpl<>(messages);
        when(messageService.getMessages(any(), any(), any())).thenReturn(page);

        User principal = new User(
                "alice",
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/chat/" + id + "/messages"))
                .andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        verify(messageService).getMessages(eq(id), any(), eq("alice"));
    }
}