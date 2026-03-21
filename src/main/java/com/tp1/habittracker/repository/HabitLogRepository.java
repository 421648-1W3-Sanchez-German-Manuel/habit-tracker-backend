package com.tp1.habittracker.repository;

import com.tp1.habittracker.domain.model.HabitLog;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HabitLogRepository extends MongoRepository<HabitLog, String> {

    List<HabitLog> findAllByHabitIdOrderByDateDesc(String habitId);

    List<HabitLogDateView> findAllProjectedByHabitIdAndDateLessThanEqualOrderByDateDesc(String habitId, LocalDate date);

    List<HabitLog> findAllByHabitIdAndDateBetweenOrderByDateAsc(String habitId, LocalDate from, LocalDate to);

    void deleteAllByHabitId(String habitId);
}
