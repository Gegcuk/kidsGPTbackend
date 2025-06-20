package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.dto.chat.Tone;
import org.springframework.security.core.userdetails.User;
import uk.gegc.kidsgptbackend.service.chat.AiChatService;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ChatControllerStandaloneTest.TestConfig.class)
class ChatControllerStandaloneTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AiChatService chatService;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        AiChatService aiChatService() {
            return Mockito.mock(AiChatService.class);
        }
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @DisplayName("POST /api/v1/chat with null principal → 401")
    void chat_nullPrincipal_returnsUnauthorized() throws Exception {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY);
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(chatService, never()).chat(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/chat with principal → 200 and service called")
    void chat_withPrincipal_callsService() throws Exception {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY);
        ChatMessageResponse resp = new ChatMessageResponse("ok", "model", 1L, 1, UUID.randomUUID());
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
}