package uk.gegc.kidsgptbackend.features.chat.api;

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
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageDto;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageRequest;
import uk.gegc.kidsgptbackend.features.chat.api.dto.ChatMessageResponse;
import uk.gegc.kidsgptbackend.features.chat.application.AiChatService;
import uk.gegc.kidsgptbackend.features.chat.application.ChatMessageService;

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

        // Log detailed request with full message content
        log.info("=== CHAT REQUEST START ===");
        log.info("User: {}", principal.getUsername());
        log.info("ContextId: {}", request.contextId());
        log.info("Tone: {}", request.tone());
        log.info("User Message: '{}'", request.message());
        log.info("Context History ({} messages):", request.context() != null ? request.context().size() : 0);
        if (request.context() != null) {
            for (int i = 0; i < request.context().size(); i++) {
                var msg = request.context().get(i);
                log.info("  [{}] {} ({}): '{}'", i, msg.role(), msg.id(), msg.content());
            }
        }
        log.info("=== CHAT REQUEST END ===");

        Principal p = principal::getUsername;
        ChatMessageResponse response = chatService.chat(request, p);
        
        // Log detailed response with full content
        log.info("=== CHAT RESPONSE START ===");
        log.info("User: {}", principal.getUsername());
        log.info("ContextId: {}", response.contextId());
        log.info("Model: {}", response.model());
        log.info("Latency: {}ms", response.latencyMs());
        log.info("Tokens Used: {}", response.tokensUsed());
        log.info("AI Reply: '{}'", response.reply());
        log.info("=== CHAT RESPONSE END ===");
        
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

        log.info("=== GET MESSAGES REQUEST START ===");
        log.info("User: {}", principal.getUsername());
        log.info("ContextId: {}", contextId);
        
        // Handle unpaged requests safely
        if (pageable.isPaged()) {
            log.info("Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        } else {
            log.info("Page: unpaged (all results)");
        }
        log.info("=== GET MESSAGES REQUEST END ===");

        Page<ChatMessageDto> page = messageService.getMessages(contextId, pageable, principal.getUsername());
        
        log.info("=== GET MESSAGES RESPONSE START ===");
        log.info("User: {}", principal.getUsername());
        log.info("ContextId: {}", contextId);
        log.info("Total Elements: {}, Current Page Size: {}, Total Pages: {}", 
            page.getTotalElements(), page.getNumberOfElements(), page.getTotalPages());
        log.info("Messages on this page ({}):", page.getNumberOfElements());
        for (int i = 0; i < page.getContent().size(); i++) {
            var msg = page.getContent().get(i);
            log.info("  [{}] {} ({}): '{}' at {}", i, msg.role(), msg.id(), msg.content(), msg.createdAt());
        }
        log.info("=== GET MESSAGES RESPONSE END ===");
        
        return ResponseEntity.ok(page);
    }
}

