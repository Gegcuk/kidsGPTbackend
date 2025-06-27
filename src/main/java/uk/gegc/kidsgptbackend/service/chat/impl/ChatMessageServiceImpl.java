package uk.gegc.kidsgptbackend.service.chat.impl;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;
import uk.gegc.kidsgptbackend.mapper.ChatMessageMapper;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.model.chat.ChatMessage;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;
import uk.gegc.kidsgptbackend.service.chat.ChatMessageService;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatContextRepository contextRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageMapper mapper;

    @Override
    public Page<ChatMessageDto> getMessages(UUID contextId, Pageable pageable, String username) {
        log.info("=== CHATMESSAGESERVICE RETRIEVAL START ===");
        log.info("ContextId: {}", contextId);
        log.info("User: {}", username);
        log.info("Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        
        ChatContext ctx = contextRepository.findById(contextId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Context not found"));
        if (!ctx.getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        log.info("Context found - Owner: {}, Created: {}", ctx.getUsername(), ctx.getCreatedAt());
        
        Page<ChatMessage> messages = messageRepository.findByContext_IdOrderByCreatedAtAsc(contextId, pageable);
        log.info("Retrieved {} total messages, returning {} on current page", 
            messages.getTotalElements(), messages.getNumberOfElements());
        
        log.info("Messages from database:");
        for (int i = 0; i < messages.getContent().size(); i++) {
            var msg = messages.getContent().get(i);
            log.info("  [{}] {} ({}): '{}' at {}", i, msg.getRole(), msg.getId(), msg.getContent(), msg.getCreatedAt());
        }
        log.info("=== CHATMESSAGESERVICE RETRIEVAL END ===");
        
        return messages.map(mapper::toDto);
    }
}