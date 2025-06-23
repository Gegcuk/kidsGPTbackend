package uk.gegc.kidsgptbackend.service.chat.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;
import uk.gegc.kidsgptbackend.mapper.ChatMessageMapper;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatMessageServiceImplTest {

    @Mock
    ChatContextRepository contextRepository;
    @Mock
    ChatMessageRepository messageRepository;
    @Mock
    ChatMessageMapper mapper;

    @InjectMocks
    ChatMessageServiceImpl service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("getMessages: context not found throws 404")
    void getMessages_contextMissing_throws() {
        UUID id = UUID.randomUUID();
        when(contextRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(id, Pageable.unpaged(), "bob"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(String.valueOf(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    @DisplayName("getMessages: username mismatch throws 403")
    void getMessages_wrongUser_throws() {
        UUID id = UUID.randomUUID();
        ChatContext ctx = new ChatContext();
        ctx.setId(id);
        ctx.setUsername("alice");
        when(contextRepository.findById(id)).thenReturn(Optional.of(ctx));

        assertThatThrownBy(() -> service.getMessages(id, Pageable.unpaged(), "bob"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(String.valueOf(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    @DisplayName("getMessages: success maps entities")
    void getMessages_success_mapsDto() {
        UUID id = UUID.randomUUID();
        ChatContext ctx = new ChatContext();
        ctx.setId(id);
        ctx.setUsername("alice");
        when(contextRepository.findById(id)).thenReturn(Optional.of(ctx));

        ChatMessage msg = new ChatMessage();
        msg.setId(UUID.randomUUID());
        msg.setRole("USER");
        msg.setContent("hi");
        msg.setCreatedAt(LocalDateTime.now());
        when(messageRepository.findByContext_IdOrderByCreatedAtAsc(eq(id), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(msg)));
        ChatMessageDto dto = new ChatMessageDto(msg.getId(), msg.getRole(), msg.getContent(), msg.getCreatedAt());
        when(mapper.toDto(msg)).thenReturn(dto);

        Page<ChatMessageDto> result = service.getMessages(id, PageRequest.of(0, 20), "alice");
        assertThat(result.getContent()).containsExactly(dto);
    }
}
