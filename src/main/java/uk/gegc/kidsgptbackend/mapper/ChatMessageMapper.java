package uk.gegc.kidsgptbackend.mapper;

import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;

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