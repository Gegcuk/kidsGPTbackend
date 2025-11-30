package uk.gegc.kidsgptbackend.features.family.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.family.domain.model.Parent;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentRepository extends JpaRepository<Parent, UUID> {
    Optional<Parent> findByEmail(String email);
    Optional<Parent> findByUserId(UUID userId);
}
