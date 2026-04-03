package com.tp1.habittracker.repository;

import com.tp1.habittracker.domain.model.Habit;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HabitRepository extends MongoRepository<Habit, String> {

    List<Habit> findAllByUserIdOrderByCreatedAtDesc(String userId);
    
    List<Habit> findAll(Sort sort);
}
