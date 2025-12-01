package uk.gegc.kidsgptbackend.features.chat.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.chat.domain.model.ChatMessage;

import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Page<ChatMessage> findByContext_IdOrderByCreatedAtAsc(UUID contextId, Pageable pageable);

}

