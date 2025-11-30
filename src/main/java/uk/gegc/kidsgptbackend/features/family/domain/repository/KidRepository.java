package uk.gegc.kidsgptbackend.features.family.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.features.family.domain.model.Kid;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KidRepository extends JpaRepository<Kid, UUID> {
    Optional<Kid> findByParentId(UUID parentId);
    List<Kid> findAllByParentId(UUID parentId);
    int countByParentId(UUID parentId);
    Optional<Kid> findByUserId(UUID userId);
}
