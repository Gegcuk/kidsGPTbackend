package uk.gegc.kidsgptbackend.service.chat;

import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;

import java.security.Principal;

public interface AiChatService {
    ChatMessageResponse chat(ChatMessageRequest request, Principal principal);
}
