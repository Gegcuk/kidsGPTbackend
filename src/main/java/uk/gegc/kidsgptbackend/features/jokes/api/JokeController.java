package uk.gegc.kidsgptbackend.features.jokes.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import uk.gegc.kidsgptbackend.features.jokes.api.dto.DailyJokeDto;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.jokes.application.DailyJokeService;
import uk.gegc.kidsgptbackend.features.subscription.application.SubscriptionAccessService;
import uk.gegc.kidsgptbackend.features.user.domain.repository.UserRepository;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/jokes")
@RequiredArgsConstructor
@Tag(name = "Jokes", description = "Daily kid-safe jokes")
public class JokeController {

    private final DailyJokeService dailyJokeService;
    private final SubscriptionAccessService subscriptionAccessService;
    private final UserRepository userRepository;

    @Operation(summary = "Get a daily joke, optionally filtered by age group", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/daily")
    public ResponseEntity<DailyJokeDto> getDailyJoke(
            @RequestParam(value = "ageGroup", required = false) String ageGroupParam,
            java.security.Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int remaining = subscriptionAccessService.getRemainingDailyFreeMessagesForSubject(user, user.getId());
        boolean hasFeatureAccess = subscriptionAccessService.hasFeatureAccess(user, "chat_limit");
        if (remaining <= 0 && !hasFeatureAccess) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        AgeGroup ageGroup = null;
        if (ageGroupParam != null) {
            try {
                ageGroup = AgeGroup.valueOf(ageGroupParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid age group parameter, will use default
                ageGroup = null;
            }
        }
        
        DailyJokeDto joke = ageGroup != null ? 
            dailyJokeService.getDailyJoke(ageGroup) : 
            dailyJokeService.getDailyJoke();

        subscriptionAccessService.incrementDailyFreeMessagesForSubject(user, user.getId());
        return ResponseEntity.ok(joke);
    }
}
