package uk.gegc.kidsgptbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageDto;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.service.chat.AiChatService;
import uk.gegc.kidsgptbackend.service.chat.ChatMessageService;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService chatService;
    private final ChatMessageService messageService;


    @PostMapping("/chat")
    public ResponseEntity<ChatMessageResponse> chat(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Chat request: user={}, contextId={}, messageLength={}, contextSize={}", 
            principal.getUsername(), request.contextId(), request.message().length(), 
            request.context() != null ? request.context().size() : 0);

        Principal p = principal::getUsername;
        ChatMessageResponse response = chatService.chat(request, p);
        
        log.info("Chat response: user={}, contextId={}, replyLength={}, model={}, latencyMs={}", 
            principal.getUsername(), response.contextId(), response.reply().length(), 
            response.model(), response.latencyMs());
        
        return ResponseEntity.ok(response);
    }


    @GetMapping("/chat/{contextId}/messages")
    public ResponseEntity<Page<ChatMessageDto>> getMessages(
            @PathVariable("contextId") java.util.UUID contextId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Get messages request: user={}, contextId={}, page={}, size={}", 
            principal.getUsername(), contextId, pageable.getPageNumber(), pageable.getPageSize());

        Page<ChatMessageDto> page = messageService.getMessages(contextId, pageable, principal.getUsername());
        
        log.info("Get messages response: user={}, contextId={}, totalElements={}, currentPageSize={}, totalPages={}", 
            principal.getUsername(), contextId, page.getTotalElements(), page.getNumberOfElements(), page.getTotalPages());
        
        return ResponseEntity.ok(page);
    }
}
