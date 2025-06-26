package uk.gegc.kidsgptbackend.dto.jokes;

import lombok.Data;

@Data
public class DailyJokeDto {
    private String joke;
    private String category; // e.g., "animals", "school", "science", "wordplay"
    private String ageGroup; // e.g., "6-8", "9-10", "11-12"
    private String imageUrl; // optional
} 