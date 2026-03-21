package com.tp1.habittracker.dto.log;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateHabitLogRequest(
        @NotBlank(message = "Habit id is required")
        String habitId,

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotNull(message = "Value is required")
        JsonNode value
) {
}
