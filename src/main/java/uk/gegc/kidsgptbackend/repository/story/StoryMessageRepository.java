package uk.gegc.kidsgptbackend.repository.story;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.story.StoryMessage;

import java.util.UUID;

@Repository
public interface StoryMessageRepository extends JpaRepository<StoryMessage, UUID> {
} 