package com.tp1.habittracker.dto.habit;

import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.enums.HabitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateHabitRequest(
        @NotBlank(message = "Habit name is required")
        @Size(min = 2, max = 100, message = "Habit name must be between 2 and 100 characters")
        String name,

        @NotNull(message = "Habit type is required")
        HabitType type,

        @NotNull(message = "Frequency is required")
        Frequency frequency
) {
}
