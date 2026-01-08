package uk.gegc.kidsgptbackend.features.jokes.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Daily kid-safe joke")
@Data
public class DailyJokeDto {
    @Schema(description = "Joke text")
    private String joke;
    @Schema(description = "Joke category")
    private String category; // e.g., "animals", "school", "science", "wordplay"
    @Schema(description = "Target age group label")
    private String ageGroup; // e.g., "6-8", "9-10", "11-12"
    @Schema(description = "Optional image URL")
    private String imageUrl; // optional
}
