package uk.gegc.kidsgptbackend.repository.family;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.family.Kid;

import java.util.UUID;

@Repository
public interface KidRepository extends JpaRepository<Kid, UUID> {
}
