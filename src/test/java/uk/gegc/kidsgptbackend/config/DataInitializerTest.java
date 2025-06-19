package uk.gegc.kidsgptbackend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
public class DataInitializerTest {

    @Mock
    RoleRepository roleRepository;

    @InjectMocks
    DataInitializer initializer;

    public DataInitializerTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("run inserts missing roles")
    void run_insertsMissingRoles() throws Exception {
        when(roleRepository.findByRole(anyString())).thenReturn(Optional.empty());

        initializer.run();

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(RoleName.values().length)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Role::getRole)
                .containsExactlyInAnyOrder(
                        RoleName.ROLE_PARENT.name(),
                        RoleName.ROLE_ADMIN.name(),
                        RoleName.ROLE_CHILD.name()
                );
    }

    @Test
    @DisplayName("run skips existing roles")
    void run_skipsExisting() throws Exception {
        when(roleRepository.findByRole(anyString())).thenReturn(Optional.of(new Role()));

        initializer.run();

        verify(roleRepository, never()).save(any());
    }
}
