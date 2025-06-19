package uk.gegc.kidsgptbackend.model.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_contexts")
@Getter
@Setter
@NoArgsConstructor
public class ChatContext {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "context_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
