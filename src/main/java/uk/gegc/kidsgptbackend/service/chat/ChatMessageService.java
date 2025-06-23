package uk.gegc.kidsgptbackend.service.chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;

import java.util.UUID;

public interface ChatMessageService {
    Page<ChatMessageDto> getMessages(UUID contextId, Pageable pageable, String username);
}
