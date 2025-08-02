package uk.gegc.kidsgptbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gegc.kidsgptbackend.dto.jokes.DailyJokeDto;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.service.jokes.DailyJokeService;

@RestController
@RequestMapping("/api/v1/jokes")
@RequiredArgsConstructor
public class JokeController {

    private final DailyJokeService dailyJokeService;

    @GetMapping("/daily")
    public ResponseEntity<DailyJokeDto> getDailyJoke(
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
        
        DailyJokeDto joke = ageGroup != null ? 
            dailyJokeService.getDailyJoke(ageGroup) : 
            dailyJokeService.getDailyJoke();
        
        return ResponseEntity.ok(joke);
    }
} 