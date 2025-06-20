package uk.gegc.kidsgptbackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.model.user.Role;
import uk.gegc.kidsgptbackend.model.user.RoleName;
import uk.gegc.kidsgptbackend.repository.user.RoleRepository;


@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;



    @Override
    public void run(String... args) throws Exception {
        for (RoleName roleName : RoleName.values()) {
            String name = roleName.name();
            if (roleRepository.findByRole(name).isEmpty()) {
                Role role = new Role();
                role.setRole(name);
                roleRepository.save(role);
                System.out.println("Inserted role: " + name);
            }
        }


    }
}
