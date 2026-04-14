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
import com.tp1.habittracker.dto.habit.HabitResponse;
import com.tp1.habittracker.service.HabitService;
import com.tp1.habittracker.service.HabitSimilarityService;
import java.time.Instant;
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
        Habit habit = Habit.builder()
                .id("habit-1")
                .userId("user-1")
                .name("Drink water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now())
                .build();

        when(habitSimilarityService.findMostSimilarHabit("Hydrate")).thenReturn(Optional.of(habit));

        ResponseEntity<?> response = controller.checkSimilarity(new CheckSimilarityRequest("Hydrate"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof HabitResponse);

        HabitResponse body = (HabitResponse) response.getBody();
        assertEquals("habit-1", body.id());
        assertEquals("Drink water", body.name());
    }

    @Test
    void checkSimilarityReturnsNotFoundWhenNoSimilarHabitExists() {
        when(habitSimilarityService.findMostSimilarHabit("Completely different")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.checkSimilarity(new CheckSimilarityRequest("Completely different"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        assertEquals(Map.of("message", "No similar habit exists"), response.getBody());
    }

    @Test
    void checkSimilarityThrowsWhenRequestIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.checkSimilarity(null)
        );

        assertEquals("Similarity request is required", exception.getMessage());
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
}
