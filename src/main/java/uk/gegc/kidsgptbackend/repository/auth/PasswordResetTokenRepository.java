package uk.gegc.kidsgptbackend.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gegc.kidsgptbackend.model.auth.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.token = :token AND prt.used = false AND prt.expiresAt > :now")
    Optional<PasswordResetToken> findValidTokenByTokenAndExpiresAtAfter(@Param("token") String token, @Param("now") LocalDateTime now);

    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.userId = :userId AND prt.used = false AND prt.expiresAt > :now ORDER BY prt.createdAt DESC")
    Optional<PasswordResetToken> findLatestValidTokenByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE PasswordResetToken prt SET prt.used = true, prt.usedAt = :usedAt WHERE prt.userId = :userId AND prt.used = false")
    void invalidateAllTokensForUser(@Param("userId") UUID userId, @Param("usedAt") LocalDateTime usedAt);

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :now OR prt.used = true")
    void deleteExpiredAndUsedTokens(@Param("now") LocalDateTime now);
} 