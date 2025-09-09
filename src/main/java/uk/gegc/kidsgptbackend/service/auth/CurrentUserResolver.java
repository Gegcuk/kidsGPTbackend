package uk.gegc.kidsgptbackend.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurrentUserResolver {

    private final UserRepository userRepository;

    /**
     * Resolve Spring Security UserDetails principal to our User entity
     */
    public User getCurrentUser(UserDetails principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Principal cannot be null");
        }

        return userRepository.findByUsername(principal.getUsername())
                .or(() -> userRepository.findByEmail(principal.getUsername()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + principal.getUsername()));
    }
}
