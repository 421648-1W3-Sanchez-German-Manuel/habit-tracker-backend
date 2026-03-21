package com.tp1.habittracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.exception.ResourceNotFoundException;
import com.tp1.habittracker.repository.HabitLogDateView;
import com.tp1.habittracker.repository.HabitLogRepository;
import com.tp1.habittracker.repository.HabitRepository;
import com.tp1.habittracker.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HabitLogRepository habitLogRepository;

    private HabitService habitService;

    @BeforeEach
    void setUp() {
        habitService = new HabitService(habitRepository, userRepository, habitLogRepository);
    }

    @Test
    void calculateCurrentStreakDailyReturnsConsecutiveDays() {
        LocalDate today = LocalDate.now();

        Habit dailyHabit = Habit.builder()
                .id("habit-1")
                .frequency(Frequency.DAILY)
                .build();

        when(habitRepository.findById("habit-1")).thenReturn(Optional.of(dailyHabit));
        when(habitLogRepository.findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(anyString(), any()))
                .thenReturn(List.of(
                        dateView(today),
                        dateView(today.minusDays(1)),
                        dateView(today.minusDays(2)),
                        dateView(today.minusDays(4))
                ));

        int streak = habitService.calculateCurrentStreak("habit-1");

        assertEquals(3, streak);
    }

    @Test
    void calculateCurrentStreakWeeklyReturnsConsecutiveWeeks() {
        LocalDate today = LocalDate.now();

        Habit weeklyHabit = Habit.builder()
                .id("habit-2")
                .frequency(Frequency.WEEKLY)
                .build();

        when(habitRepository.findById("habit-2")).thenReturn(Optional.of(weeklyHabit));
        when(habitLogRepository.findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(anyString(), any()))
                .thenReturn(List.of(
                        dateView(today),
                        dateView(today.minusWeeks(1)),
                        dateView(today.minusWeeks(3))
                ));

        int streak = habitService.calculateCurrentStreak("habit-2");

        assertEquals(2, streak);
    }

    @Test
    void calculateCompletionLast7DaysDailyRoundsToTwoDecimals() {
        LocalDate today = LocalDate.now();

        Habit dailyHabit = Habit.builder()
                .id("habit-3")
                .frequency(Frequency.DAILY)
                .build();

        when(habitRepository.findById("habit-3")).thenReturn(Optional.of(dailyHabit));
        when(habitLogRepository.findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(anyString(), any()))
                .thenReturn(List.of(
                        dateView(today),
                        dateView(today.minusDays(3))
                ));

        double completion = habitService.calculateCompletionLast7Days("habit-3");

        assertEquals(28.57, completion);
    }

    @Test
    void calculateCurrentStreakThrowsWhenHabitDoesNotExist() {
        when(habitRepository.findById("missing-habit")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> habitService.calculateCurrentStreak("missing-habit"));
    }

    private HabitLogDateView dateView(LocalDate date) {
        return new HabitLogDateView() {
            @Override
            public LocalDate getDate() {
                return date;
            }
        };
    }
}
