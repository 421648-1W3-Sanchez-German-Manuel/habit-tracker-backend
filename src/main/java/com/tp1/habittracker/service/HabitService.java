package com.tp1.habittracker.service;

import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.dto.habit.CreateHabitRequest;
import com.tp1.habittracker.dto.habit.HabitStreakResponse;
import com.tp1.habittracker.dto.habit.UpdateHabitRequest;
import com.tp1.habittracker.exception.DuplicateResourceException;
import com.tp1.habittracker.exception.ResourceNotFoundException;
import com.tp1.habittracker.repository.HabitLogDateView;
import com.tp1.habittracker.repository.HabitLogRepository;
import com.tp1.habittracker.repository.HabitRepository;
import com.tp1.habittracker.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HabitService {

    public record AddDefaultHabitResult(Habit habit, boolean created) {
    }

    private record StreakSnapshot(int currentStreak, LocalDate lastCompletedAt) {
    }

    private final HabitRepository habitRepository;
    private final UserRepository userRepository;
    private final HabitLogRepository habitLogRepository;
    private final OllamaClient ollamaClient;
    private final HabitSimilarityService habitSimilarityService;

    public Habit createHabit(String authenticatedUserId, CreateHabitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String userId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");
        String normalizedName = request.name().trim();

        if (!userExists(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        habitSimilarityService.findDeterministicDuplicateForUserOrDefault(userId, normalizedName)
            .ifPresent(similarHabit -> {
                throwDuplicateResourceException(similarHabit, 1.0d, "EXACT");
            });

        List<Double> embedding = ollamaClient.generateEmbedding(normalizedName);

        habitSimilarityService.findMostSimilarHabitForUserOrDefault(userId, embedding)
            .ifPresent(match -> {
                throwDuplicateResourceException(match.habit(), match.score(), "SEMANTIC");
            });

        Habit habit = Habit.builder()
                .userId(userId)
            .isDefault(false)
                .sourceDefaultHabitId(null)
                .name(normalizedName)
                .type(request.type())
                .frequency(request.frequency())
                .createdAt(Instant.now())
                .embedding(embedding)
                .build();

        return habitRepository.save(habit);
    }

    private void throwDuplicateResourceException(Habit similarHabit, double similarityScore, String matchType) {
        Map<String, Object> similarHabitDetails = new HashMap<>();
        similarHabitDetails.put("id", similarHabit.getId());
        similarHabitDetails.put("userId", similarHabit.getUserId());
        similarHabitDetails.put("name", similarHabit.getName());
        similarHabitDetails.put("type", similarHabit.getType());
        similarHabitDetails.put("frequency", similarHabit.getFrequency());
        similarHabitDetails.put("createdAt", similarHabit.getCreatedAt());
        similarHabitDetails.put("isDefault", similarHabit.isDefault());

        Map<String, Object> details = new HashMap<>();
        details.put("matchType", matchType);
        details.put("similarityScore", similarityScore);
        details.put("similarHabit", similarHabitDetails);

        throw new DuplicateResourceException(
                "Similar habit found: " + similarHabit.getName(),
                details
        );
    }

    public List<Habit> getHabitsByUserId(String authenticatedUserId) {
        String validatedUserId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");

        if (!userExists(validatedUserId)) {
            throw new ResourceNotFoundException("User not found with id: " + validatedUserId);
        }

        return habitRepository.findAllByUserIdOrderByCreatedAtDesc(validatedUserId);
    }

    public List<HabitStreakResponse> getHabitsWithStreaks(String authenticatedUserId) {
        String validatedUserId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");

        List<Habit> habits = getHabitsByUserId(validatedUserId);
        List<HabitStreakResponse> habitsWithStreaks = new ArrayList<>(habits.size());

        for (Habit habit : habits) {
            StreakSnapshot snapshot = computeStreakSnapshot(habit);
            habitsWithStreaks.add(new HabitStreakResponse(
                    habit.getId(),
                    snapshot.currentStreak(),
                    snapshot.lastCompletedAt()
            ));
        }

        return habitsWithStreaks;
    }

    public List<Habit> getAllHabits() {
        return habitRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public List<Habit> getDefaultHabits() {
        return habitRepository.findAllByIsDefaultTrueOrderByCreatedAtDesc();
    }

    public AddDefaultHabitResult addDefaultHabitToUser(String authenticatedUserId, String defaultHabitId) {
        String validatedUserId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");
        String validatedDefaultHabitId = Objects.requireNonNull(defaultHabitId, "defaultHabitId must not be null");

        if (!userExists(validatedUserId)) {
            throw new ResourceNotFoundException("User not found with id: " + validatedUserId);
        }

        Habit defaultHabit = habitRepository.findByIdAndIsDefaultTrue(validatedDefaultHabitId)
                .orElseThrow(() -> new ResourceNotFoundException("Default habit not found with id: " + validatedDefaultHabitId));

        Habit existingHabit = habitRepository
                .findFirstByUserIdAndNameIgnoreCaseAndTypeAndFrequency(
                        validatedUserId,
                        defaultHabit.getName(),
                        defaultHabit.getType(),
                        defaultHabit.getFrequency()
                )
                .orElse(null);

        if (existingHabit != null) {
            return new AddDefaultHabitResult(existingHabit, false);
        }

        Habit newUserHabit = Habit.builder()
                .userId(validatedUserId)
                .isDefault(false)
            .sourceDefaultHabitId(validatedDefaultHabitId)
                .name(defaultHabit.getName())
                .type(defaultHabit.getType())
                .frequency(defaultHabit.getFrequency())
                .createdAt(Instant.now())
                .embedding(defaultHabit.getEmbedding() == null ? null : new ArrayList<>(defaultHabit.getEmbedding()))
                .build();

        Habit savedHabit = habitRepository.save(newUserHabit);
        return new AddDefaultHabitResult(savedHabit, true);
    }

    public Habit updateHabit(String authenticatedUserId, String habitId, UpdateHabitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String validatedUserId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");

        Habit existingHabit = getOwnedHabitOrThrow(validatedUserId, validatedHabitId);

        String updatedName = request.name().trim();

        if (existingHabit.getSourceDefaultHabitId() != null) {
            throw new IllegalArgumentException("Default habits cannot be edited");
        }

        existingHabit.setName(updatedName);
        existingHabit.setType(request.type());
        existingHabit.setFrequency(request.frequency());
        List<Double> embedding = ollamaClient.generateEmbedding(updatedName);
        existingHabit.setEmbedding(embedding);

        return habitRepository.save(existingHabit);
    }

    public void deleteHabit(String authenticatedUserId, String habitId) {
        String validatedUserId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");

        getOwnedHabitOrThrow(validatedUserId, validatedHabitId);

        habitLogRepository.deleteAllByHabitId(validatedHabitId);
        habitRepository.deleteById(validatedHabitId);
    }

    // Generate a simple deterministic embedding vector for internal use.
    // This uses SHA-256 of the input text and converts to a fixed-size vector of doubles.
    private List<Double> generateEmbeddingFor(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes());
            List<Double> vector = new ArrayList<>();
            // produce 8-dimension vector by grouping hash bytes
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            for (int i = 0; i < 8; i++) {
                long piece = buffer.getLong();
                vector.add((double) (piece % 1000000) / 1000000.0);
            }
            return vector;
        } catch (NoSuchAlgorithmException e) {
            // fallback deterministic pseudo-random using UUID
            UUID u = UUID.nameUUIDFromBytes(text.getBytes());
            List<Double> vector = new ArrayList<>();
            long msb = u.getMostSignificantBits();
            long lsb = u.getLeastSignificantBits();
            for (int i = 0; i < 8; i++) {
                long val = (i % 2 == 0) ? msb : lsb;
                vector.add((double) (Math.abs(val) % 1000000) / 1000000.0);
            }
            return vector;
        }
    }

    public int calculateCurrentStreak(String authenticatedUserId, String habitId) {
        String validatedUserId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");
        Habit habit = getOwnedHabitOrThrow(validatedUserId, validatedHabitId);
        return computeStreakSnapshot(habit).currentStreak();
    }

    public double calculateCompletionLast7Days(String authenticatedUserId, String habitId) {
        String validatedUserId = Objects.requireNonNull(authenticatedUserId, "authenticated userId must not be null");
        String validatedHabitId = Objects.requireNonNull(habitId, "habitId must not be null");
        LocalDate today = LocalDate.now();
        LocalDate fromDate = today.minusDays(6);

        Habit habit = getOwnedHabitOrThrow(validatedUserId, validatedHabitId);

        List<HabitLogDateView> logDates = habitLogRepository
                .findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(validatedHabitId, today);

        double completion = switch (habit.getFrequency()) {
            case DAILY -> calculateDailyCompletionLast7Days(logDates, fromDate, today);
            case WEEKLY -> calculateWeeklyCompletionLast7Days(logDates, fromDate, today);
            case MONTHLY -> calculateMonthlyCompletionLast7Days(logDates, fromDate, today);
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

    private int calculateMonthlyStreak(List<HabitLogDateView> logDates) {
        Set<YearMonth> completedMonths = new HashSet<>();

        for (HabitLogDateView log : logDates) {
            completedMonths.add(YearMonth.from(log.getDate()));
        }

        YearMonth currentMonth = YearMonth.from(LocalDate.now());
        if (!completedMonths.contains(currentMonth)) {
            return 0;
        }

        int streak = 0;
        while (completedMonths.contains(currentMonth)) {
            streak++;
            currentMonth = currentMonth.minusMonths(1);
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

    private double calculateMonthlyCompletionLast7Days(List<HabitLogDateView> logDates, LocalDate fromDate, LocalDate toDate) {
        Set<YearMonth> expectedMonths = new HashSet<>();
        Set<YearMonth> completedMonths = new HashSet<>();

        LocalDate currentDate = fromDate;
        while (!currentDate.isAfter(toDate)) {
            expectedMonths.add(YearMonth.from(currentDate));
            currentDate = currentDate.plusDays(1);
        }

        for (HabitLogDateView log : logDates) {
            LocalDate date = log.getDate();
            if (!date.isBefore(fromDate) && !date.isAfter(toDate)) {
                completedMonths.add(YearMonth.from(date));
            }
        }

        int totalExpectedMonths = expectedMonths.size();
        int completedMonthCount = completedMonths.size();
        return completedMonthCount == 0 ? 0d : (completedMonthCount * 100d) / totalExpectedMonths;
    }

    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String toWeekKey(LocalDate date, WeekFields weekFields) {
        int weekBasedYear = date.get(weekFields.weekBasedYear());
        int weekOfYear = date.get(weekFields.weekOfWeekBasedYear());
        return weekBasedYear + "-" + weekOfYear;
    }

    private StreakSnapshot computeStreakSnapshot(Habit habit) {
        LocalDate today = LocalDate.now();
        List<HabitLogDateView> logDates = habitLogRepository
                .findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(habit.getId(), today);

        if (logDates.isEmpty()) {
            return new StreakSnapshot(0, null);
        }

        int streak = switch (habit.getFrequency()) {
            case DAILY -> calculateDailyStreak(logDates);
            case WEEKLY -> calculateWeeklyStreak(logDates);
            case MONTHLY -> calculateMonthlyStreak(logDates);
        };

        return new StreakSnapshot(streak, logDates.get(0).getDate());
    }

    private Habit getOwnedHabitOrThrow(String authenticatedUserId, String habitId) {
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found with id: " + habitId));

        if (!Objects.equals(habit.getUserId(), authenticatedUserId)) {
            throw new ResourceNotFoundException("Habit not found with id: " + habitId);
        }

        return habit;
    }

    private boolean userExists(String userId) {
        try {
            return userRepository.existsById(UUID.fromString(userId));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
