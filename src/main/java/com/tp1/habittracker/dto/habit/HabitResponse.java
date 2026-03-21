package com.tp1.habittracker.dto.habit;

import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.enums.HabitType;
import java.time.Instant;

public record HabitResponse(
        String id,
        String userId,
        String name,
        HabitType type,
        Frequency frequency,
        Instant createdAt
) {
}
