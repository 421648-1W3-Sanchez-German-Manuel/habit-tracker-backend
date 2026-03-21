package com.tp1.habittracker.service;

import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.dto.habit.CreateHabitRequest;
import com.tp1.habittracker.dto.habit.UpdateHabitRequest;
import com.tp1.habittracker.exception.ResourceNotFoundException;
import com.tp1.habittracker.repository.HabitLogDateView;
import com.tp1.habittracker.repository.HabitLogRepository;
import com.tp1.habittracker.repository.HabitRepository;
import com.tp1.habittracker.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HabitService {

    private final HabitRepository habitRepository;
    private final UserRepository userRepository;
    private final HabitLogRepository habitLogRepository;

    @SuppressWarnings("null")
    public Habit createHabit(CreateHabitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String userId = Objects.requireNonNull(request.userId(), "userId must not be null");
        String normalizedName = request.name().trim();

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        Habit habit = Habit.builder()
                .userId(userId)
                .name(normalizedName)
                .type(request.type())
                .frequency(request.frequency())
                .createdAt(Instant.now())
                .build();

        return habitRepository.save(habit);
    }

    public List<Habit> getHabitsByUserId(String userId) {
        String validatedUserId = Objects.requireNonNull(userId, "userId must not be null");

        if (!userRepository.existsById(validatedUserId)) {
            throw new ResourceNotFoundException("User not found with id: " + validatedUserId);
        }

        return habitRepository.findAllByUserIdOrderByCreatedAtDesc(validatedUserId);
    }

    public Habit updateHabit(String habitId, UpdateHabitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");

        Habit existingHabit = habitRepository.findById(validatedHabitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + validatedHabitId));

        existingHabit.setName(request.name().trim());
        existingHabit.setType(request.type());
        existingHabit.setFrequency(request.frequency());

        return habitRepository.save(existingHabit);
    }

    public void deleteHabit(String habitId) {
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");

        if (!habitRepository.existsById(validatedHabitId)) {
            throw new ResourceNotFoundException("Habit not found with id: " + validatedHabitId);
        }

        habitLogRepository.deleteAllByHabitId(validatedHabitId);
        habitRepository.deleteById(validatedHabitId);
    }

    public int calculateCurrentStreak(String habitId) {
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");
        LocalDate today = LocalDate.now();

        Habit habit = habitRepository.findById(validatedHabitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + validatedHabitId));

        List<HabitLogDateView> logDates = habitLogRepository
            .findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(validatedHabitId, today);
        if (logDates.isEmpty()) {
            return 0;
        }

        return switch (habit.getFrequency()) {
            case DAILY -> calculateDailyStreak(logDates);
            case WEEKLY -> calculateWeeklyStreak(logDates);
        };
    }

    public double calculateCompletionLast7Days(String habitId) {
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");
        LocalDate today = LocalDate.now();
        LocalDate fromDate = today.minusDays(6);

        Habit habit = habitRepository.findById(validatedHabitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + validatedHabitId));

        List<HabitLogDateView> logDates = habitLogRepository
                .findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(validatedHabitId, today);

        double completion = switch (habit.getFrequency()) {
            case DAILY -> calculateDailyCompletionLast7Days(logDates, fromDate, today);
            case WEEKLY -> calculateWeeklyCompletionLast7Days(logDates, fromDate, today);
        };

        return roundToTwoDecimals(completion);
    }

    private int calculateDailyStreak(List<HabitLogDateView> logDates) {
        Set<LocalDate> completedDates = new HashSet<>();
        for (HabitLogDateView log : logDates) {
            completedDates.add(log.getDate());
        }

        LocalDate currentDate = LocalDate.now();
        if (!completedDates.contains(currentDate)) {
            return 0;
        }

        int streak = 0;
        while (completedDates.contains(currentDate)) {
            streak++;
            currentDate = currentDate.minusDays(1);
        }
        return streak;
    }

    private int calculateWeeklyStreak(List<HabitLogDateView> logDates) {
        Set<String> completedWeeks = new HashSet<>();
        WeekFields weekFields = WeekFields.ISO;

        for (HabitLogDateView log : logDates) {
            completedWeeks.add(toWeekKey(log.getDate(), weekFields));
        }

        LocalDate currentDate = LocalDate.now();
        String currentWeekKey = toWeekKey(currentDate, weekFields);
        if (!completedWeeks.contains(currentWeekKey)) {
            return 0;
        }

        int streak = 0;
        while (completedWeeks.contains(currentWeekKey)) {
            streak++;
            currentDate = currentDate.minusWeeks(1);
            currentWeekKey = toWeekKey(currentDate, weekFields);
        }
        return streak;
    }

    private double calculateDailyCompletionLast7Days(List<HabitLogDateView> logDates, LocalDate fromDate, LocalDate toDate) {
        Set<LocalDate> completedDates = new HashSet<>();
        for (HabitLogDateView log : logDates) {
            LocalDate date = log.getDate();
            if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                completedDates.add(date);
            }
        }

        int totalExpectedDays = 7;
        int completedDays = completedDates.size();
        return completedDays == 0 ? 0d : (completedDays * 100d) / totalExpectedDays;
    }

    private double calculateWeeklyCompletionLast7Days(List<HabitLogDateView> logDates, LocalDate fromDate, LocalDate toDate) {
        WeekFields weekFields = WeekFields.ISO;
        Set<String> expectedWeeks = new HashSet<>();
        Set<String> completedWeeks = new HashSet<>();

        LocalDate currentDate = fromDate;
        while (!currentDate.isAfter(toDate)) {
            expectedWeeks.add(toWeekKey(currentDate, weekFields));
            currentDate = currentDate.plusDays(1);
        }

        for (HabitLogDateView log : logDates) {
            LocalDate date = log.getDate();
            if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                completedWeeks.add(toWeekKey(date, weekFields));
            }
        }

        int totalExpectedWeeks = expectedWeeks.size();
        int completedWeekCount = completedWeeks.size();
        return completedWeekCount == 0 ? 0d : (completedWeekCount * 100d) / totalExpectedWeeks;
    }

    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String toWeekKey(LocalDate date, WeekFields weekFields) {
        int weekBasedYear = date.get(weekFields.weekBasedYear());
        int weekOfYear = date.get(weekFields.weekOfWeekBasedYear());
        return weekBasedYear + "-" + weekOfYear;
    }
}
