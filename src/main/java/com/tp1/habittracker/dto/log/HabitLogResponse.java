package com.tp1.habittracker.dto.log;

import java.time.LocalDate;

public record HabitLogResponse(
        String id,
        String habitId,
        LocalDate date,
        Object value
) {
}
