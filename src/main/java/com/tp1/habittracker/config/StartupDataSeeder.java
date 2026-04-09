package com.tp1.habittracker.config;

import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.enums.HabitType;
import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.domain.model.HabitLog;
import com.tp1.habittracker.domain.model.User;
import com.tp1.habittracker.repository.HabitLogRepository;
import com.tp1.habittracker.repository.HabitRepository;
import com.tp1.habittracker.repository.UserRepository;
import com.tp1.habittracker.service.OllamaClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StartupDataSeeder implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupDataSeeder.class);

    private final UserRepository userRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final OllamaClient ollamaClient;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
                ensureDefaultHabits();

        if (hasExistingData()) {
            LOGGER.info("Skipping startup seed because data already exists.");
            return;
        }

        User user = userRepository.save(User.builder()
                .username("demo_user")
                .email("demo.user@example.com")
                .password(passwordEncoder.encode("seed-password"))
                .build());

                if (user == null || user.getId() == null) {
                        LOGGER.warn("Skipping startup seed because repositories are not returning persisted entities.");
                        return;
                }

        Habit hydrationHabit = habitRepository.save(Habit.builder()
                .userId(user.getId().toString())
                .isDefault(false)
                .name("Drink 2L water")
                .type(HabitType.BOOLEAN)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .embedding(ollamaClient.generateEmbedding("Drink 2L water"))
                .build());

        Habit readingHabit = habitRepository.save(Habit.builder()
                .userId(user.getId().toString())
                .isDefault(false)
                .name("Read pages")
                .type(HabitType.NUMBER)
                .frequency(Frequency.DAILY)
                .createdAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .embedding(ollamaClient.generateEmbedding("Read pages"))
                .build());
                
        Habit planningHabit = habitRepository.save(Habit.builder()
                .userId(user.getId().toString())
                .isDefault(false)
                .name("Weekly planning")
                .type(HabitType.TEXT)
                .frequency(Frequency.WEEKLY)
                .createdAt(Instant.now().minus(14, ChronoUnit.DAYS))
                .embedding(ollamaClient.generateEmbedding("Weekly planning"))
                .build());

                if (hydrationHabit == null || readingHabit == null || planningHabit == null) {
                        LOGGER.warn("Skipping habit log seed because repositories are not returning persisted habits.");
                        return;
                }

        LocalDate today = LocalDate.now();
        List<HabitLog> habitLogs = List.of(
                HabitLog.builder().habitId(hydrationHabit.getId()).date(today.minusDays(2)).value(true).build(),
                HabitLog.builder().habitId(hydrationHabit.getId()).date(today.minusDays(1)).value(true).build(),
                HabitLog.builder().habitId(hydrationHabit.getId()).date(today).value(true).build(),
                HabitLog.builder().habitId(readingHabit.getId()).date(today.minusDays(2)).value(18.0).build(),
                HabitLog.builder().habitId(readingHabit.getId()).date(today.minusDays(1)).value(24.0).build(),
                HabitLog.builder().habitId(readingHabit.getId()).date(today).value(30.0).build(),
                HabitLog.builder().habitId(planningHabit.getId()).date(today.minusDays(7)).value("Planned top 3 priorities").build(),
                HabitLog.builder().habitId(planningHabit.getId()).date(today).value("Reviewed and planned next week").build()
        );
        habitLogRepository.saveAll(habitLogs);

        LOGGER.info("Startup seed inserted: 1 user, 3 habits, 8 habit logs.");
    }

        private void ensureDefaultHabits() {
                seedDefaultHabit("Drink 2L water", HabitType.BOOLEAN, Frequency.DAILY, 7);
                seedDefaultHabit("Read pages", HabitType.NUMBER, Frequency.DAILY, 5);
                seedDefaultHabit("Weekly planning", HabitType.TEXT, Frequency.WEEKLY, 14);
        }

        private void seedDefaultHabit(String name, HabitType type, Frequency frequency, long daysAgo) {
                if (habitRepository.existsByNameAndIsDefaultTrue(name)) {
                        return;
                }

                habitRepository.save(Habit.builder()
                                .userId(null)
                                .isDefault(true)
                                .name(name)
                                .type(type)
                                .frequency(frequency)
                                .createdAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS))
                                .embedding(ollamaClient.generateEmbedding(name))
                                .build());
        }

    private boolean hasExistingData() {
                return userRepository.count() > 0 || habitLogRepository.count() > 0;
    }
}
