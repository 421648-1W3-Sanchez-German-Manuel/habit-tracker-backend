package com.tp1.habittracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tp1.habittracker.config.HabitSimilarityProperties;
import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.enums.HabitType;
import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.repository.HabitRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HabitSimilarityServiceTest {

    private OllamaClient ollamaClient;
    private HabitRepository habitRepository;
    private HabitSimilarityProperties properties;
    private HabitSimilarityService service;

    @BeforeEach
    void setUp() {
        ollamaClient = mock(OllamaClient.class);
        habitRepository = mock(HabitRepository.class);
        properties = new HabitSimilarityProperties();
        properties.setSimilarityThreshold(0.8);
        service = new HabitSimilarityService(ollamaClient, habitRepository, properties);
    }

    @Test
    void findMostSimilarHabitReturnsBestMatchWhenSimilarityAboveThreshold() {
        // Query embedding
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        // Habit with high similarity
        List<Double> highSimilarityEmbedding = List.of(0.95, 0.1, 0.0);
        Habit similarHabit = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(highSimilarityEmbedding)
                .build();

        // Habit with low similarity
        List<Double> lowSimilarityEmbedding = List.of(0.0, 1.0, 0.0);
        Habit dissimilarHabit = Habit.builder()
                .id("habit-2")
                .userId("user-1")
                .name("Do yoga")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(lowSimilarityEmbedding)
                .build();

        when(ollamaClient.generateEmbedding("Drink water")).thenReturn(queryEmbedding);
        when(habitRepository.findAll()).thenReturn(List.of(similarHabit, dissimilarHabit));

        Optional<Habit> result = service.findMostSimilarHabit("Drink water");

        assertTrue(result.isPresent());
        assertEquals("habit-1", result.get().getId());
    }

    @Test
    void findMostSimilarHabitReturnsEmptyWhenBestMatchBelowThreshold() {
        // Query embedding
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        // Habit with similarity below threshold
        List<Double> lowSimilarityEmbedding = List.of(0.0, 0.9, 0.1);
        Habit dissimilarHabit = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Do yoga")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(lowSimilarityEmbedding)
                .build();

        when(ollamaClient.generateEmbedding("Meditate")).thenReturn(queryEmbedding);
        when(habitRepository.findAll()).thenReturn(List.of(dissimilarHabit));

        Optional<Habit> result = service.findMostSimilarHabit("Meditate");

        assertTrue(result.isEmpty());
    }

    @Test
    void findMostSimilarHabitIgnoresHabitsWithNullEmbedding() {
        // Query embedding
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        // Habit with null embedding
        Habit habitWithNullEmbedding = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Old habit")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(null)
                .build();

        // Habit with valid embedding above threshold
        List<Double> validEmbedding = List.of(0.95, 0.1, 0.0);
        Habit validHabit = Habit.builder()
                .id("habit-2")
                .userId("user-1")
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(validEmbedding)
                .build();

        when(ollamaClient.generateEmbedding("Drink water")).thenReturn(queryEmbedding);
        when(habitRepository.findAll()).thenReturn(List.of(habitWithNullEmbedding, validHabit));

        Optional<Habit> result = service.findMostSimilarHabit("Drink water");

        assertTrue(result.isPresent());
        assertEquals("habit-2", result.get().getId());
    }

    @Test
    void findMostSimilarHabitIgnoresHabitsWithEmptyEmbedding() {
        // Query embedding
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        // Habit with empty embedding
        Habit habitWithEmptyEmbedding = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Empty habit")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(List.of())
                .build();

        // Habit with valid embedding above threshold
        List<Double> validEmbedding = List.of(0.95, 0.1, 0.0);
        Habit validHabit = Habit.builder()
                .id("habit-2")
                .userId("user-1")
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(validEmbedding)
                .build();

        when(ollamaClient.generateEmbedding("Drink water")).thenReturn(queryEmbedding);
        when(habitRepository.findAll()).thenReturn(List.of(habitWithEmptyEmbedding, validHabit));

        Optional<Habit> result = service.findMostSimilarHabit("Drink water");

        assertTrue(result.isPresent());
        assertEquals("habit-2", result.get().getId());
    }

    @Test
    void findMostSimilarHabitReturnsEmptyWhenBlankInput() {
        Optional<Habit> result = service.findMostSimilarHabit("   ");

        assertTrue(result.isEmpty());
    }

    @Test
    void findMostSimilarHabitReturnsEmptyWhenNoHabitsExist() {
        // Query embedding
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        when(ollamaClient.generateEmbedding("Drink water")).thenReturn(queryEmbedding);
        when(habitRepository.findAll()).thenReturn(List.of());

        Optional<Habit> result = service.findMostSimilarHabit("Drink water");

        assertTrue(result.isEmpty());
    }

        @Test
        void findMostSimilarHabitForUserOrDefaultReturnsExactMatchBeforeSemanticSearch() {
        Habit defaultHabit = Habit.builder()
            .id("habit-default")
            .userId(null)
            .isDefault(true)
            .name("Drink 2lt of water")
            .embedding(List.of(0.1, 0.2, 0.3))
            .build();

        when(habitRepository.findAllByUserIdOrIsDefaultTrue("user-1")).thenReturn(List.of(defaultHabit));

        Optional<HabitSimilarityService.HabitSimilarityMatch> result =
            service.findMostSimilarHabitForUserOrDefault("user-1", "Drink water");

        assertTrue(result.isPresent());
        assertEquals("habit-default", result.get().habit().getId());
        assertEquals(1.0d, result.get().score());
        verifyNoInteractions(ollamaClient);
        }

        @Test
        void findMostSimilarHabitForUserOrDefaultIsCaseAndWhitespaceInsensitiveForExactMatches() {
        Habit userHabit = Habit.builder()
            .id("habit-user")
            .userId("user-1")
            .name("Drink 2lt of water")
            .embedding(List.of(0.1, 0.2, 0.3))
            .build();

        when(habitRepository.findAllByUserIdOrIsDefaultTrue("user-1")).thenReturn(List.of(userHabit));

        Optional<HabitSimilarityService.HabitSimilarityMatch> result =
            service.findMostSimilarHabitForUserOrDefault("user-1", "  drink water  ");

        assertTrue(result.isPresent());
        assertEquals("habit-user", result.get().habit().getId());
        verifyNoInteractions(ollamaClient);
        }

    @Test
    void findMostSimilarHabitSelectsBestMatchWhenMultipleAboveThreshold() {
        // Query embedding
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0, 0.0);

        // Habit 1 with similarity ~0.95 (dot product ~0.95, magnitudes ~1.0)
        List<Double> embedding1 = List.of(0.95, 0.1, 0.0, 0.0);

        // Habit 2 with similarity ~0.87 (dot product ~0.87, magnitudes ~1.0)
        List<Double> embedding2 = List.of(0.87, 0.3, 0.35, 0.0);

        Habit habit1 = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(embedding1)
                .build();

        Habit habit2 = Habit.builder()
                .id("habit-2")
                .userId("user-1")
                .name("Hydration")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(embedding2)
                .build();

        when(ollamaClient.generateEmbedding("Drink water")).thenReturn(queryEmbedding);
        when(habitRepository.findAll()).thenReturn(List.of(habit1, habit2));

        Optional<Habit> result = service.findMostSimilarHabit("Drink water");

        assertTrue(result.isPresent());
        assertEquals("habit-1", result.get().getId());
    }

    @Test
    void findMostSimilarHabitThrowsWhenNewHabitNameIsNull() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> service.findMostSimilarHabit(null)
        );
    }

    @Test
    void findMostSimilarHabitReturnsEmptyWhenAllHabitsHaveBadEmbeddings() {
        // Query embedding
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        // Habit with mismatched embedding dimensions (will cause IllegalArgumentException)
        List<Double> badEmbedding = List.of(1.0, 0.0); // Missing third dimension
        Habit badHabit = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Bad habit")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .embedding(badEmbedding)
                .build();

        when(ollamaClient.generateEmbedding("Drink water")).thenReturn(queryEmbedding);
        when(habitRepository.findAll()).thenReturn(List.of(badHabit));

        Optional<Habit> result = service.findMostSimilarHabit("Drink water");

        assertTrue(result.isEmpty());
    }

        @Test
        void findMostSimilarHabitForUserOrDefaultIncludesDefaultsAndUserHabits() {
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        Habit userHabit = Habit.builder()
            .id("habit-user")
            .userId("user-1")
            .name("Drink water")
            .embedding(List.of(1.0, 0.0, 0.0))
            .build();

        Habit defaultHabit = Habit.builder()
            .id("habit-default")
            .userId(null)
            .isDefault(true)
            .name("Hydration")
            .embedding(List.of(0.7, 0.7, 0.0))
            .build();

        when(habitRepository.findAllByUserIdOrIsDefaultTrue("user-1")).thenReturn(List.of(userHabit, defaultHabit));

        Optional<HabitSimilarityService.HabitSimilarityMatch> result =
            service.findMostSimilarHabitForUserOrDefault("user-1", queryEmbedding);

        assertTrue(result.isPresent());
        assertEquals("habit-user", result.get().habit().getId());
        assertTrue(result.get().score() >= 0.8);
        verify(habitRepository).findAllByUserIdOrIsDefaultTrue("user-1");
        }

        @Test
        void findMostSimilarHabitForUserOrDefaultReturnsEmptyWhenNoCandidateReachesThreshold() {
        List<Double> queryEmbedding = List.of(1.0, 0.0, 0.0);

        Habit lowSimilarityCandidate = Habit.builder()
            .id("habit-low")
            .userId("user-1")
            .name("Do yoga")
            .embedding(List.of(0.1, 0.95, 0.0))
            .build();

        when(habitRepository.findAllByUserIdOrIsDefaultTrue("user-1")).thenReturn(List.of(lowSimilarityCandidate));

        Optional<HabitSimilarityService.HabitSimilarityMatch> result =
            service.findMostSimilarHabitForUserOrDefault("user-1", queryEmbedding);

        assertFalse(result.isPresent());
        }
}
