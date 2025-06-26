package uk.gegc.kidsgptbackend.repository.story;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.story.Story;
import uk.gegc.kidsgptbackend.model.story.StoryStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoryRepository extends JpaRepository<Story, UUID> {
    
    Page<Story> findByUsernameOrderByUpdatedAtDesc(String username, Pageable pageable);
    
    Optional<Story> findByIdAndUsername(UUID id, String username);
    
    @Query("SELECT s FROM Story s WHERE s.username = :username AND s.status = :status ORDER BY s.updatedAt DESC")
    Page<Story> findByUsernameAndStatus(@Param("username") String username, @Param("status") StoryStatus status, Pageable pageable);
    
    long countByUsername(String username);
} 