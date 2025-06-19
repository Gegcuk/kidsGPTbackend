package uk.gegc.kidsgptbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageRequest;
import uk.gegc.kidsgptbackend.dto.chat.ChatMessageResponse;
import uk.gegc.kidsgptbackend.service.chat.AiChatService;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final AiChatService chatService;

    @PostMapping("/chat")
    public ResponseEntity<ChatMessageResponse> chat(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal User principal
    ) {
        if (request.tone() == null ||
                !("FRIENDLY".equalsIgnoreCase(request.tone()) ||
                        "FORMAL".equalsIgnoreCase(request.tone()) ||
                        "FUN".equalsIgnoreCase(request.tone()))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ChatMessageResponse response = chatService.chat(request, (Principal) principal);
        return ResponseEntity.ok(response);
    }
}
