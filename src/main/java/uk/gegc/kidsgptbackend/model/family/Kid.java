package uk.gegc.kidsgptbackend.model.family;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "kids")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Kid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "kid_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "favorite_color")
    private String favoriteColor;

    @Column(name = "avatar_id")
    private String avatarId;

    @Column(name = "interests")
    private String interests; // Comma-separated list for now

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group")
    private AgeGroup ageGroup;

    public void updateAgeGroupFromBirthDate() {
        if (birthDate != null) {
            int age = java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
            try {
                this.ageGroup = AgeGroup.fromAge(age);
            } catch (IllegalArgumentException e) {
                this.ageGroup = null;
            }
        }
    }
}
