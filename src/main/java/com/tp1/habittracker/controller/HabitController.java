package com.tp1.habittracker.controller;

import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.dto.habit.CreateHabitRequest;
import com.tp1.habittracker.dto.habit.HabitResponse;
import com.tp1.habittracker.dto.habit.UpdateHabitRequest;
import com.tp1.habittracker.service.HabitService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public ResponseEntity<HabitResponse> createHabit(@Valid @RequestBody CreateHabitRequest request) {
        Habit habit = habitService.createHabit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(habit));
    }

    @GetMapping("/user/{userId}")
    public List<HabitResponse> getHabitsByUser(@PathVariable String userId) {
        return habitService.getHabitsByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{habitId}/streak")
    public ResponseEntity<Integer> getCurrentStreak(@PathVariable String habitId) {
        int streak = habitService.calculateCurrentStreak(habitId);
        return ResponseEntity.ok(streak);
    }

    @GetMapping("/{habitId}/completion")
    public ResponseEntity<Double> getCompletionLast7Days(@PathVariable String habitId) {
        double completion = habitService.calculateCompletionLast7Days(habitId);
        return ResponseEntity.ok(completion);
    }

    @PutMapping("/{id}")
    public HabitResponse updateHabit(@PathVariable("id") String habitId, @Valid @RequestBody UpdateHabitRequest request) {
        Habit updatedHabit = habitService.updateHabit(habitId, request);
        return toResponse(updatedHabit);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHabit(@PathVariable("id") String habitId) {
        habitService.deleteHabit(habitId);
        return ResponseEntity.noContent().build();
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
