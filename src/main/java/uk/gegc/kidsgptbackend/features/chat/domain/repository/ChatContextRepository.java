package uk.gegc.kidsgptbackend.features.chat.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.chat.domain.model.ChatContext;

import java.util.UUID;

@Repository
public interface ChatContextRepository extends JpaRepository<ChatContext, UUID> {
}

