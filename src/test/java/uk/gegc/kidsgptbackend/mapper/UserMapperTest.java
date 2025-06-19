package uk.gegc.kidsgptbackend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gegc.kidsgptbackend.dto.user.UserDto;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
public class UserMapperTest {

    @Mock
    RoleRepository roleRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserMapper mapper;

    public UserMapperTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("toDto maps entity fields correctly")
    void toDto_mapsFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(Set.of(new Role(1L, RoleName.ROLE_PARENT.name(), null)));

        UserDto dto = mapper.toDto(user);
        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.roles()).containsExactly(RoleName.ROLE_PARENT);
        assertThat(dto.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(dto.updatedAt()).isEqualTo(user.getUpdatedAt());
    }
}
