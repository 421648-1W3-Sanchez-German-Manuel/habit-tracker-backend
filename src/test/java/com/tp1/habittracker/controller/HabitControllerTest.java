package com.tp1.habittracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.enums.HabitType;
import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.dto.habit.CheckSimilarityRequest;
import com.tp1.habittracker.dto.habit.CheckSimilarityResponse;
import com.tp1.habittracker.dto.habit.HabitResponse;
import com.tp1.habittracker.dto.habit.HabitStreakResponse;
import com.tp1.habittracker.service.HabitService;
import com.tp1.habittracker.service.HabitSimilarityService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

class HabitControllerTest {

    private HabitService habitService;
    private HabitSimilarityService habitSimilarityService;
    private HabitController controller;

    @BeforeEach
    void setUp() {
        habitService = mock(HabitService.class);
        habitSimilarityService = mock(HabitSimilarityService.class);
        controller = new HabitController(habitService, habitSimilarityService);
    }

    @SuppressWarnings("null")
    @Test
    void checkSimilarityReturnsHabitWhenFound() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-1");

        Habit habit = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
            .isDefault(false)
                .createdAt(Instant.now())
                .build();

        when(habitSimilarityService.findMostSimilarHabitForUserOrDefault("user-1", "Hydrate"))
            .thenReturn(Optional.of(new HabitSimilarityService.HabitSimilarityMatch(habit, 0.91d)));

        ResponseEntity<?> response = controller.checkSimilarity(authentication, new CheckSimilarityRequest("Hydrate"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof CheckSimilarityResponse);

        CheckSimilarityResponse body = (CheckSimilarityResponse) response.getBody();
        assertEquals("habit-1", body.habit().id());
        assertEquals("Drink water", body.habit().name());
        assertEquals("My Habits", body.belongsTo());
    }

    @Test
    void checkSimilarityReturnsNotFoundWhenNoSimilarHabitExists() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-1");

        when(habitSimilarityService.findMostSimilarHabitForUserOrDefault("user-1", "Completely different"))
            .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.checkSimilarity(
            authentication,
            new CheckSimilarityRequest("Completely different")
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        assertEquals(Map.of("message", "No similar habit exists"), response.getBody());
    }

    @Test
        void checkSimilarityThrowsWhenAuthenticationIsMissing() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
            () -> controller.checkSimilarity(null, new CheckSimilarityRequest("Hydrate"))
        );

        assertEquals("Authenticated user is required", exception.getMessage());
    }

    @Test
    void getDefaultHabitsReturnsDefaultHabits() {
        Habit habit = Habit.builder()
                .id("habit-default")
                .userId(null)
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .isDefault(true)
                .createdAt(Instant.now())
                .build();

        when(habitService.getDefaultHabits()).thenReturn(List.of(habit));

        List<HabitResponse> response = controller.getDefaultHabits();

        assertEquals(1, response.size());
        assertEquals("habit-default", response.get(0).id());
        assertTrue(response.get(0).name().equals("Drink water"));
    }

    @Test
    void addDefaultHabitToCurrentUserReturnsCreatedWhenHabitIsNew() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-1");

        Habit habit = Habit.builder()
                .id("habit-user-1")
                .userId("user-1")
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .isDefault(false)
                .build();

        when(habitService.addDefaultHabitToUser("user-1", "default-1"))
                .thenReturn(new HabitService.AddDefaultHabitResult(habit, true));

        ResponseEntity<HabitResponse> response = controller.addDefaultHabitToCurrentUser("default-1", authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("habit-user-1", response.getBody().id());
        assertEquals("user-1", response.getBody().userId());
    }

    @Test
    void addDefaultHabitToCurrentUserReturnsOkWhenHabitAlreadyExists() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-1");

        Habit existingHabit = Habit.builder()
                .id("habit-existing")
                .userId("user-1")
                .name("Read pages")
                .type(HabitType.NUMBER)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .isDefault(false)
                .build();

        when(habitService.addDefaultHabitToUser("user-1", "default-2"))
                .thenReturn(new HabitService.AddDefaultHabitResult(existingHabit, false));

        ResponseEntity<HabitResponse> response = controller.addDefaultHabitToCurrentUser("default-2", authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("habit-existing", response.getBody().id());
    }

    @Test
    void getHabitsWithStreaksReturnsCurrentUserStreaks() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user-1");

        HabitStreakResponse streakResponse = new HabitStreakResponse("habit-1", 4, LocalDate.of(2026, 4, 14));
        when(habitService.getHabitsWithStreaks("user-1")).thenReturn(List.of(streakResponse));

        List<HabitStreakResponse> response = controller.getHabitsWithStreaks(authentication);

        assertEquals(1, response.size());
        assertEquals("habit-1", response.get(0).habitId());
        assertEquals(4, response.get(0).currentStreak());
        assertEquals(LocalDate.of(2026, 4, 14), response.get(0).lastCompletedAt());
    }
}
