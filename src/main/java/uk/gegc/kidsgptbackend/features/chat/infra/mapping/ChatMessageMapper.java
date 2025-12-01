package uk.gegc.kidsgptbackend.features.chat.infra.mapping;

import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto;
import uk.gegc.kidsgptbackend.features.chat.domain.model.ChatMessage;

@Component
public class ChatMessageMapper {
    public ChatMessageDto toDto(ChatMessage message) {
        return new ChatMessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}

