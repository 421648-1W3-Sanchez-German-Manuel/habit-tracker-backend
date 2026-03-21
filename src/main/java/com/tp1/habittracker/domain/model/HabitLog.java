package com.tp1.habittracker.domain.model;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "habit_logs")
@CompoundIndex(name = "habit_date_idx", def = "{'habitId': 1, 'date': -1}")
public class HabitLog {

    @Id
    private String id;

    private String habitId;

    private LocalDate date;

    private Object value;
}
