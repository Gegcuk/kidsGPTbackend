package uk.gegc.kidsgptbackend.features.chat.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto;

import java.util.UUID;

public interface ChatMessageService {
    Page<ChatMessageDto> getMessages(UUID contextId, Pageable pageable, String username);
}

