package uk.gegc.kidsgptbackend.features.user.infra.mapping;

import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.features.user.api.dto.*;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.features.user.domain.model.Role;
import uk.gegc.kidsgptbackend.features.user.domain.model.RoleName;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isActive(),
                user.getRoles()
                        .stream()
                        .map(Role::getRole)
                        .map(RoleName::valueOf)
                        .collect(Collectors.toSet()),
                user.getCreatedAt(),
                user.getLastLoginDate(),
                user.getUpdatedAt()
        );
    }

    public UserProfileDto toProfileDto(User user) {
        RoleName role = user.getRoles() != null ? user.getRoles().stream()
                .findFirst()
                .map(Role::getRole)
                .map(RoleName::valueOf)
                .orElse(null) : null;
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                role,
                user.getCreatedAt()
        );
    }

    public static KidDto toKidDto(Kid kid) {
        // Guard against null/lazy user relation
        String username = (kid.getUser() != null) ? kid.getUser().getUsername() : null;
        
        return new KidDto(
            kid.getId(),
            kid.getNickname(),
            username,
            kid.getAgeGroup(),
            kid.getFavoriteColor(),
            kid.getAvatarId(),
            kid.getInterests()
        );
    }

    public static ChildProfileDto toChildProfileDto(Kid kid) {
        // Use specific age if available, otherwise calculate from age group
        int age = 0;
        if (kid.getAge() != null) {
            age = kid.getAge();
        } else if (kid.getAgeGroup() != null) {
            age = (kid.getAgeGroup().getMinAge() + kid.getAgeGroup().getMaxAge()) / 2;
        }
        
        return new ChildProfileDto(
            kid.getId(),
            kid.getNickname(),
            age,
            kid.getInterests(),
            kid.getAvatarId(),
            kid.getAgeGroup()
        );
    }

    public static void updateKidFromRequest(Kid kid, ChildProfileUpdateRequest req) {
        kid.setNickname(req.name());
        kid.setAge(req.age()); // Set the specific age
        kid.setInterests(req.interests());
        kid.setAvatarId(req.avatarId());
        // Set age group from age if possible
        try {
            kid.setAgeGroup(uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup.fromAge(req.age()));
        } catch (IllegalArgumentException e) {
            // Keep existing age group if provided age doesn't match any group
        }
    }

}
