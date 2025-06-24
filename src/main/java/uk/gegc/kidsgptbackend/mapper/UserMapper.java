package uk.gegc.kidsgptbackend.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.dto.user.UserProfileDto;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.model.family.Kid;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileDto;
import uk.gegc.kidsgptbackend.dto.user.ChildProfileUpdateRequest;
import java.time.LocalDate;
import java.time.Period;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

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
        RoleName role = user.getRoles().stream()
                .findFirst()
                .map(Role::getRole)
                .map(RoleName::valueOf)
                .orElse(null);
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                role,
                user.getCreatedAt()
        );
    }

    public static ChildProfileDto toChildProfileDto(Kid kid) {
        int age = 0;
        if (kid.getBirthDate() != null) {
            age = Period.between(kid.getBirthDate(), LocalDate.now()).getYears();
        }
        return new ChildProfileDto(
            kid.getId(),
            kid.getFirstName(), // For now, use firstName as name
            age,
            kid.getInterests(),
            kid.getAvatarId()
        );
    }

    public static void updateKidFromRequest(Kid kid, ChildProfileUpdateRequest req) {
        kid.setFirstName(req.name()); // For now, use name as firstName
        // Set birthDate from age (approximate: set to Jan 1st of birth year)
        int birthYear = LocalDate.now().getYear() - req.age();
        kid.setBirthDate(LocalDate.of(birthYear, 1, 1));
        kid.setInterests(req.interests());
        kid.setAvatarId(req.avatarId());
    }

}
