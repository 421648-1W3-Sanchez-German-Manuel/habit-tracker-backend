package com.tp1.habittracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tp1.habittracker.domain.enums.HabitType;
import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.domain.model.HabitLog;
import com.tp1.habittracker.dto.log.CreateHabitLogRequest;
import com.tp1.habittracker.exception.ResourceNotFoundException;
import com.tp1.habittracker.repository.HabitLogRepository;
import com.tp1.habittracker.repository.HabitRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HabitLogService {

    private final HabitLogRepository habitLogRepository;
    private final HabitRepository habitRepository;

    @SuppressWarnings("null")
    public HabitLog addHabitLog(CreateHabitLogRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Habit habit = habitRepository.findById(request.habitId())
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + request.habitId()));

        Object parsedValue = parseAndValidateValue(habit.getType(), request.value());

        HabitLog habitLog = HabitLog.builder()
                .habitId(habit.getId())
                .date(request.date())
                .value(parsedValue)
                .build();

        return habitLogRepository.save(habitLog);
    }

    public List<HabitLog> getLogsByHabit(String habitId) {
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");
        ensureHabitExists(validatedHabitId);
        return habitLogRepository.findAllByHabitIdOrderByDateDesc(validatedHabitId);
    }

    public List<HabitLog> getLogsByHabitAndDateRange(String habitId, LocalDate from, LocalDate to) {
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");
        Objects.requireNonNull(from, "from date must not be null");
        Objects.requireNonNull(to, "to date must not be null");

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must be before or equal to 'to' date");
        }

        ensureHabitExists(validatedHabitId);
        return habitLogRepository.findAllByHabitIdAndDateBetweenOrderByDateAsc(validatedHabitId, from, to);
    }

    private void ensureHabitExists(String habitId) {
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");
        if (!habitRepository.existsById(validatedHabitId)) {
            throw new ResourceNotFoundException("Habit not found with id: " + validatedHabitId);
        }
    }

    private Object parseAndValidateValue(HabitType type, JsonNode valueNode) {
        return switch (type) {
            case BOOLEAN -> parseBooleanValue(valueNode);
            case NUMBER -> parseNumberValue(valueNode);
            case TEXT -> parseTextValue(valueNode);
        };
    }

    private Boolean parseBooleanValue(JsonNode valueNode) {
        if (!valueNode.isBoolean()) {
            throw new IllegalArgumentException("Value must be a boolean for BOOLEAN habit type");
        }
        return valueNode.booleanValue();
    }

    private Double parseNumberValue(JsonNode valueNode) {
        if (!valueNode.isNumber()) {
            throw new IllegalArgumentException("Value must be a number for NUMBER habit type");
        }
        return valueNode.doubleValue();
    }

    private String parseTextValue(JsonNode valueNode) {
        if (!valueNode.isTextual() || valueNode.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Value must be a non-empty text for TEXT habit type");
        }
        return valueNode.textValue().trim();
    }
}
