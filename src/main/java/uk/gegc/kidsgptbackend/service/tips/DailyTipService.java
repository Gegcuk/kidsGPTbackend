package uk.gegc.kidsgptbackend.service.tips;

import uk.gegc.kidsgptbackend.dto.tips.DailyTipDto;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;

public interface DailyTipService {
    
    /**
     * Generates a fun educational fact appropriate for the given age group
     * @param ageGroup The age group enum
     * @return A daily tip/fact
     */
    DailyTipDto getDailyTip(AgeGroup ageGroup);
    
    /**
     * Generates a fun educational fact for the default age group (9-10)
     * @return A daily tip/fact
     */
    DailyTipDto getDailyTip();
} 