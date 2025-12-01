package uk.gegc.kidsgptbackend.features.story.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.story.domain.model.StoryMessage;

import java.util.UUID;

@Repository
public interface StoryMessageRepository extends JpaRepository<StoryMessage, UUID> {
}

