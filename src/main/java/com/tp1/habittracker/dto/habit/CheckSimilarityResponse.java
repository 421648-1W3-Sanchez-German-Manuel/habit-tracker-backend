package com.tp1.habittracker.dto.habit;

public record CheckSimilarityResponse(
        HabitResponse habit,
        String belongsTo
) {
}