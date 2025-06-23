package uk.gegc.kidsgptbackend.service.chat.impl;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;
import uk.gegc.kidsgptbackend.mapper.ChatMessageMapper;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;
import uk.gegc.kidsgptbackend.repository.chat.ChatContextRepository;
import uk.gegc.kidsgptbackend.repository.chat.ChatMessageRepository;
import uk.gegc.kidsgptbackend.service.chat.ChatMessageService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatContextRepository contextRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageMapper mapper;

    @Override
    public Page<ChatMessageDto> getMessages(UUID contextId, Pageable pageable, String username) {
        ChatContext ctx = contextRepository.findById(contextId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Context not found"));
        if (!ctx.getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return messageRepository.findByContext_IdOrderByCreatedAtAsc(contextId, pageable)
                .map(mapper::toDto);
    }
}