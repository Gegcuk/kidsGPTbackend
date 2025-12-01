package uk.gegc.kidsgptbackend.features.chat.application;

import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageRequest;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageResponse;

import java.security.Principal;

public interface AiChatService {
    ChatMessageResponse chat(ChatMessageRequest request, Principal principal);
}

