package uk.gegc.kidsgptbackend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.chat.ChatContext;

import java.util.UUID;

@Repository
public interface ChatContextRepository extends JpaRepository<ChatContext, UUID> {
}
