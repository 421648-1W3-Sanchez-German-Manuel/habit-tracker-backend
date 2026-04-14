package com.tp1.habittracker.dto.habit;

import java.time.LocalDate;

public record HabitStreakResponse(
        String habitId,
        int currentStreak,
        LocalDate lastCompletedAt
) {
}