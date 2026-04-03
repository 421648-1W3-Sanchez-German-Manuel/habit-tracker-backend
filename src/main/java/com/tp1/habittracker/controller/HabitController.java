package com.tp1.habittracker.controller;

import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.dto.habit.CheckSimilarityRequest;
import com.tp1.habittracker.dto.habit.CreateHabitRequest;
import com.tp1.habittracker.dto.habit.HabitResponse;
import com.tp1.habittracker.dto.habit.UpdateHabitRequest;
import com.tp1.habittracker.exception.ResourceNotFoundException;
import com.tp1.habittracker.service.HabitSimilarityService;
import com.tp1.habittracker.service.HabitService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;
    private final HabitSimilarityService habitSimilarityService;

    @PostMapping
    public ResponseEntity<HabitResponse> createHabit(
            Authentication authentication,
            @Valid @RequestBody CreateHabitRequest request
    ) {
        Habit habit = habitService.createHabit(extractAuthenticatedUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(habit));
    }

    @GetMapping("/user/{userId}")
    public List<HabitResponse> getHabitsByUser(@PathVariable String userId, Authentication authentication) {
        String authenticatedUserId = extractAuthenticatedUserId(authentication);
        if (!authenticatedUserId.equals(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        return habitService.getHabitsByUserId(authenticatedUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping
    public List<HabitResponse> getAllHabits() {
        return habitService.getAllHabits()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{habitId}/streak")
    public ResponseEntity<Integer> getCurrentStreak(@PathVariable String habitId, Authentication authentication) {
        int streak = habitService.calculateCurrentStreak(extractAuthenticatedUserId(authentication), habitId);
        return ResponseEntity.ok(streak);
    }

    @GetMapping("/{habitId}/completion")
    public ResponseEntity<Double> getCompletionLast7Days(@PathVariable String habitId, Authentication authentication) {
        double completion = habitService.calculateCompletionLast7Days(extractAuthenticatedUserId(authentication), habitId);
        return ResponseEntity.ok(completion);
    }

    @PutMapping("/{id}")
    public HabitResponse updateHabit(
            @PathVariable("id") String habitId,
            Authentication authentication,
            @Valid @RequestBody UpdateHabitRequest request
    ) {
        Habit updatedHabit = habitService.updateHabit(extractAuthenticatedUserId(authentication), habitId, request);
        return toResponse(updatedHabit);
    }

    @PostMapping("/check-similarity")
    public ResponseEntity<?> checkSimilarity(@Valid @RequestBody CheckSimilarityRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Similarity request is required");
        }

        return habitSimilarityService.findMostSimilarHabit(request.name())
                .<ResponseEntity<?>>map(habit -> ResponseEntity.ok(toResponse(habit)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No similar habit exists")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHabit(@PathVariable("id") String habitId, Authentication authentication) {
        habitService.deleteHabit(extractAuthenticatedUserId(authentication), habitId);
        return ResponseEntity.noContent().build();
    }

    private String extractAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return authentication.getName();
    }

    private HabitResponse toResponse(Habit habit) {
        return new HabitResponse(
                habit.getId(),
                habit.getUserId(),
                habit.getName(),
                habit.getType(),
                habit.getFrequency(),
                habit.getCreatedAt()
        );
    }
}
