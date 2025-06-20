package uk.gegc.kidsgptbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gegc.kidsgptbackend.dto.auth.AuthLoginRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.dto.chat.Tone;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.chat.AiChatService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ChatControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    AiChatService aiChatService;

    @org.junit.jupiter.api.BeforeEach
    void setupUser() {
        roleRepository.findByRole("ROLE_PARENT").orElseGet(() -> {
            uk.gegc.kidsgptbackend.model.user.Role r = new uk.gegc.kidsgptbackend.model.user.Role();
            r.setRole("ROLE_PARENT");
            return roleRepository.save(r);
        });

        User u = new User();
        u.setUsername("chatuser");
        u.setEmail("chat@example.com");
        u.setHashedPassword(passwordEncoder.encode("password123"));
        u.setActive(true);
        u.setRoles(java.util.Set.of(roleRepository.findByRole("ROLE_PARENT").get()));
        userRepository.save(u);
    }

    private String obtainAccessToken() throws Exception {
        AuthLoginRequest req = new AuthLoginRequest("chatuser", "password123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(response);
        return node.get("accessToken").asText();
    }

    @Test
    @DisplayName("POST /api/v1/chat without token → 401")
    void chat_noToken_returnsUnauthorized() throws Exception {
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY);
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/chat with token → 200 and body")
    void chat_withToken_returnsOk() throws Exception {
        ChatMessageResponse resp = new ChatMessageResponse("ok", "model", 1L, 1, UUID.randomUUID());
        when(aiChatService.chat(any(ChatMessageRequest.class), any())).thenReturn(resp);

        String token = obtainAccessToken();
        ChatMessageRequest req = new ChatMessageRequest("hi", null, Tone.FRIENDLY);
        String response = mockMvc.perform(post("/api/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("\"reply\":\"ok\"");
    }

}
