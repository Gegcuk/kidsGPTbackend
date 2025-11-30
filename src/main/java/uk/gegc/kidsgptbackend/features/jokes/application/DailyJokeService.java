package uk.gegc.kidsgptbackend.features.jokes.application;

import uk.gegc.kidsgptbackend.features.jokes.api.dto.DailyJokeDto;
import uk.gegc.kidsgptbackend.features.user.domain.model.AgeGroup;

public interface DailyJokeService {
    
    /**
     * Generates a fun, age-appropriate joke for the given age group
     * @param ageGroup The age group enum
     * @return A daily joke
     */
    DailyJokeDto getDailyJoke(AgeGroup ageGroup);
    
    /**
     * Generates a fun, age-appropriate joke for the default age group (9-10)
     * @return A daily joke
     */
    DailyJokeDto getDailyJoke();
} 