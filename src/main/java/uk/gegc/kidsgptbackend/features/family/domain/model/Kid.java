package uk.gegc.kidsgptbackend.features.family.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;
import uk.gegc.kidsgptbackend.features.user.domain.model.User;

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

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "age")
    private Integer age;

    @Column(name = "favorite_color")
    private String favoriteColor;

    @Column(name = "avatar_id")
    private String avatarId;

    @Column(name = "interests")
    private String interests; // Comma-separated list for now

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false)
    private AgeGroup ageGroup;
}
