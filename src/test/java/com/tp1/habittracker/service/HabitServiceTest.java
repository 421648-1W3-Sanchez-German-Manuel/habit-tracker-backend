package com.tp1.habittracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tp1.habittracker.domain.enums.HabitType;
import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.dto.habit.CreateHabitRequest;
import com.tp1.habittracker.exception.DuplicateResourceException;
import com.tp1.habittracker.exception.ResourceNotFoundException;
import com.tp1.habittracker.repository.HabitLogDateView;
import com.tp1.habittracker.repository.HabitLogRepository;
import com.tp1.habittracker.repository.HabitRepository;
import com.tp1.habittracker.repository.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private HabitSimilarityService habitSimilarityService;


    private HabitService habitService;

    @BeforeEach
    void setUp() {
        habitService = new HabitService(
            habitRepository,
            userRepository,
            habitLogRepository,
            ollamaClient,
            habitSimilarityService
        );
    }

    @Test
    void calculateCurrentStreakDailyReturnsConsecutiveDays() {
        LocalDate today = LocalDate.now();

        Habit dailyHabit = Habit.builder()
                .id("habit-1")
            .userId("user-1")
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

        int streak = habitService.calculateCurrentStreak("user-1", "habit-1");

        assertEquals(3, streak);
    }

    @Test
    void calculateCurrentStreakWeeklyReturnsConsecutiveWeeks() {
        LocalDate today = LocalDate.now();

        Habit weeklyHabit = Habit.builder()
                .id("habit-2")
            .userId("user-1")
                .frequency(Frequency.WEEKLY)
                .build();

        when(habitRepository.findById("habit-2")).thenReturn(Optional.of(weeklyHabit));
        when(habitLogRepository.findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(anyString(), any()))
                .thenReturn(List.of(
                        dateView(today),
                        dateView(today.minusWeeks(1)),
                        dateView(today.minusWeeks(3))
                ));

        int streak = habitService.calculateCurrentStreak("user-1", "habit-2");

        assertEquals(2, streak);
    }

    @Test
    void calculateCompletionLast7DaysDailyRoundsToTwoDecimals() {
        LocalDate today = LocalDate.now();

        Habit dailyHabit = Habit.builder()
                .id("habit-3")
            .userId("user-1")
                .frequency(Frequency.DAILY)
                .build();

        when(habitRepository.findById("habit-3")).thenReturn(Optional.of(dailyHabit));
        when(habitLogRepository.findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(anyString(), any()))
                .thenReturn(List.of(
                        dateView(today),
                        dateView(today.minusDays(3))
                ));

        double completion = habitService.calculateCompletionLast7Days("user-1", "habit-3");

        assertEquals(28.57, completion);
    }

    @Test
    void calculateCurrentStreakThrowsWhenHabitDoesNotExist() {
        when(habitRepository.findById("missing-habit")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> habitService.calculateCurrentStreak("user-1", "missing-habit"));
    }

    @Test
    void createHabitGeneratesEmbeddingOnceAndPersistsIt() {
        String userId = UUID.randomUUID().toString();
        CreateHabitRequest request = new CreateHabitRequest(userId, "Drink water", HabitType.BOOLEAN, Frequency.DAILY);
        List<Double> embedding = List.of(0.12, 0.34, 0.56);

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(true);
        when(ollamaClient.generateEmbedding("Drink water")).thenReturn(embedding);
        when(habitSimilarityService.findMostSimilarHabitForUserOrDefault(userId, embedding)).thenReturn(Optional.empty());
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit created = habitService.createHabit(userId, request);

        verify(ollamaClient, times(1)).generateEmbedding(eq("Drink water"));
        verify(habitRepository, times(1)).save(any(Habit.class));
        assertEquals(embedding, created.getEmbedding());
    }

    @Test
    void createHabitTrimsNameBeforeGeneratingEmbedding() {
        String userId = UUID.randomUUID().toString();
        CreateHabitRequest request = new CreateHabitRequest(userId, "  Read pages  ", HabitType.NUMBER, Frequency.DAILY);
        List<Double> embedding = new ArrayList<>(List.of(0.9, 0.1));

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(true);
        when(ollamaClient.generateEmbedding("Read pages")).thenReturn(embedding);
        when(habitSimilarityService.findMostSimilarHabitForUserOrDefault(userId, embedding)).thenReturn(Optional.empty());
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit created = habitService.createHabit(userId, request);

        verify(ollamaClient, times(1)).generateEmbedding(eq("Read pages"));
        assertEquals("Read pages", created.getName());
        assertEquals(embedding, created.getEmbedding());
    }

    @Test
    void getDefaultHabitsReturnsOnlyDefaultHabits() {
        Habit defaultHabit = Habit.builder()
                .id("default-1")
                .name("Drink water")
                .isDefault(true)
                .build();

        when(habitRepository.findAllByIsDefaultTrueOrderByCreatedAtDesc()).thenReturn(List.of(defaultHabit));

        List<Habit> habits = habitService.getDefaultHabits();

        assertEquals(1, habits.size());
        assertEquals("default-1", habits.get(0).getId());
        verify(habitRepository, times(1)).findAllByIsDefaultTrueOrderByCreatedAtDesc();
    }

        @Test
        void addDefaultHabitToUserCreatesHabitWhenNoDuplicateExists() {
        String userId = UUID.randomUUID().toString();
        Habit defaultHabit = Habit.builder()
            .id("default-1")
            .userId(null)
            .name("Drink water")
            .type(HabitType.BOOLEAN)
            .frequency(Frequency.DAILY)
            .isDefault(true)
            .embedding(List.of(0.1, 0.2, 0.3))
            .build();

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(true);
        when(habitRepository.findByIdAndIsDefaultTrue("default-1")).thenReturn(Optional.of(defaultHabit));
        when(habitRepository.findFirstByUserIdAndNameIgnoreCaseAndTypeAndFrequency(
            userId,
            "Drink water",
            HabitType.BOOLEAN,
            Frequency.DAILY
        )).thenReturn(Optional.empty());
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitService.AddDefaultHabitResult result = habitService.addDefaultHabitToUser(userId, "default-1");

        assertTrue(result.created());
        assertEquals(userId, result.habit().getUserId());
        assertFalse(result.habit().isDefault());
        assertEquals("Drink water", result.habit().getName());
        assertEquals(HabitType.BOOLEAN, result.habit().getType());
        assertEquals(Frequency.DAILY, result.habit().getFrequency());
        assertEquals(defaultHabit.getEmbedding(), result.habit().getEmbedding());
        verify(habitRepository, times(1)).save(any(Habit.class));
        }

        @Test
        void addDefaultHabitToUserReturnsExistingHabitWhenDuplicateExists() {
        String userId = UUID.randomUUID().toString();
        Habit defaultHabit = Habit.builder()
            .id("default-1")
            .userId(null)
            .name("Read pages")
            .type(HabitType.NUMBER)
            .frequency(Frequency.DAILY)
            .isDefault(true)
            .build();
        Habit existingUserHabit = Habit.builder()
            .id("user-habit-1")
            .userId(userId)
            .name("Read pages")
            .type(HabitType.NUMBER)
            .frequency(Frequency.DAILY)
            .isDefault(false)
            .build();

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(true);
        when(habitRepository.findByIdAndIsDefaultTrue("default-1")).thenReturn(Optional.of(defaultHabit));
        when(habitRepository.findFirstByUserIdAndNameIgnoreCaseAndTypeAndFrequency(
            userId,
            "Read pages",
            HabitType.NUMBER,
            Frequency.DAILY
        )).thenReturn(Optional.of(existingUserHabit));

        HabitService.AddDefaultHabitResult result = habitService.addDefaultHabitToUser(userId, "default-1");

        assertFalse(result.created());
        assertEquals("user-habit-1", result.habit().getId());
        verify(habitRepository, times(0)).save(any(Habit.class));
        }

        @Test
        void addDefaultHabitToUserThrowsWhenDefaultHabitDoesNotExist() {
        String userId = UUID.randomUUID().toString();

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(true);
        when(habitRepository.findByIdAndIsDefaultTrue("missing-default")).thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> habitService.addDefaultHabitToUser(userId, "missing-default")
        );
        }

        @Test
        void addDefaultHabitToUserThrowsWhenUserDoesNotExist() {
        String userId = UUID.randomUUID().toString();

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(false);

        assertThrows(
            ResourceNotFoundException.class,
            () -> habitService.addDefaultHabitToUser(userId, "default-1")
        );
        }

        @Test
        void createHabitThrowsWhenSimilarUserHabitExists() {
        String userId = UUID.randomUUID().toString();
        CreateHabitRequest request = new CreateHabitRequest(userId, "Hydration", HabitType.BOOLEAN, Frequency.DAILY);
        List<Double> embedding = List.of(0.5, 0.5, 0.1);

        Habit similarHabit = Habit.builder()
            .id("habit-existing")
            .userId(userId)
            .name("Drink water")
            .type(HabitType.BOOLEAN)
            .frequency(Frequency.DAILY)
            .isDefault(false)
            .build();

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(true);
        when(ollamaClient.generateEmbedding("Hydration")).thenReturn(embedding);
        when(habitSimilarityService.findMostSimilarHabitForUserOrDefault(userId, embedding))
            .thenReturn(Optional.of(new HabitSimilarityService.HabitSimilarityMatch(similarHabit, 0.93)));

        assertThrows(DuplicateResourceException.class, () -> habitService.createHabit(userId, request));
        }

        @Test
        void createHabitThrowsWhenSimilarDefaultHabitExists() {
        String userId = UUID.randomUUID().toString();
        CreateHabitRequest request = new CreateHabitRequest(userId, "Read 20 pages", HabitType.NUMBER, Frequency.DAILY);
        List<Double> embedding = List.of(0.3, 0.2, 0.8);

        Habit defaultHabit = Habit.builder()
            .id("default-1")
            .userId(null)
            .name("Read pages")
            .type(HabitType.NUMBER)
            .frequency(Frequency.DAILY)
            .isDefault(true)
            .build();

        when(userRepository.existsById(UUID.fromString(userId))).thenReturn(true);
        when(ollamaClient.generateEmbedding("Read 20 pages")).thenReturn(embedding);
        when(habitSimilarityService.findMostSimilarHabitForUserOrDefault(userId, embedding))
            .thenReturn(Optional.of(new HabitSimilarityService.HabitSimilarityMatch(defaultHabit, 0.9)));

        assertThrows(DuplicateResourceException.class, () -> habitService.createHabit(userId, request));
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
