package uk.gegc.kidsgptbackend.features.tips.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gegc.kidsgptbackend.features.tips.api.dto.DailyTipDto;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.features.tips.application.DailyTipService;

@RestController
@RequestMapping("/api/v1/tips")
@RequiredArgsConstructor
public class TipController {

    private final DailyTipService dailyTipService;

    @GetMapping("/daily")
    public ResponseEntity<DailyTipDto> getDailyTip(
            @RequestParam(value = "ageGroup", required = false) String ageGroupParam
    ) {
        AgeGroup ageGroup = null;
        if (ageGroupParam != null) {
            try {
                ageGroup = AgeGroup.valueOf(ageGroupParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid age group parameter, will use default
                ageGroup = null;
            }
        }
        
        DailyTipDto tip = ageGroup != null ? 
            dailyTipService.getDailyTip(ageGroup) : 
            dailyTipService.getDailyTip();
        
        return ResponseEntity.ok(tip);
    }
} 