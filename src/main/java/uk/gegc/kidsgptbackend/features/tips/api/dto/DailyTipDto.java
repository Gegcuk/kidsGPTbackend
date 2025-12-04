package uk.gegc.kidsgptbackend.features.tips.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Daily educational tip for kids")
@Data
public class DailyTipDto {
    @Schema(description = "Tip text")
    private String fact;
    @Schema(description = "Tip category, e.g., science/history")
    private String category; // e.g., "science", "history", "nature", "space"
    @Schema(description = "Target age group label")
    private String ageGroup; // e.g., "6-8", "9-10", "11-12"
    @Schema(description = "Optional image URL")
    private String imageUrl; // optional
}
