package uk.gegc.kidsgptbackend.service.jokes;

import uk.gegc.kidsgptbackend.dto.jokes.DailyJokeDto;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

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