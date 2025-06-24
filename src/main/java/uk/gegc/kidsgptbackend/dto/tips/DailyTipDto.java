package uk.gegc.kidsgptbackend.dto.tips;

import lombok.Data;

@Data
public class DailyTipDto {
    private String fact;
    private String category; // e.g., "science", "history", "nature", "space"
    private String ageGroup; // e.g., "6-8", "9-10", "11-12"
    private String imageUrl; // optional
} 