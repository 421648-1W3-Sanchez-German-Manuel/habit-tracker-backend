package com.tp1.habittracker.repository;

import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.domain.enums.Frequency;
import com.tp1.habittracker.domain.enums.HabitType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HabitRepository extends MongoRepository<Habit, String> {

    List<Habit> findAllByUserIdOrderByCreatedAtDesc(String userId);

    List<Habit> findAllByIsDefaultTrueOrderByCreatedAtDesc();

    List<Habit> findAllByUserIdOrIsDefaultTrue(String userId);

    boolean existsByNameAndIsDefaultTrue(String name);

    Optional<Habit> findByIdAndIsDefaultTrue(String id);

    Optional<Habit> findFirstByUserIdAndNameIgnoreCaseAndTypeAndFrequency(
            String userId,
            String name,
            HabitType type,
            Frequency frequency
    );
}
