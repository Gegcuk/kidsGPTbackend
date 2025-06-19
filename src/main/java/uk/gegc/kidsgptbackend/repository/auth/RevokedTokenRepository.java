package uk.gegc.kidsgptbackend.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.auth.RevokedToken;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    boolean existsByToken(String token);
    long deleteByExpiresAtBefore(LocalDateTime time);
}
