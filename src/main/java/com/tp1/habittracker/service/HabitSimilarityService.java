package com.tp1.habittracker.service;

import com.tp1.habittracker.config.HabitSimilarityProperties;
import com.tp1.habittracker.domain.model.Habit;
import com.tp1.habittracker.repository.HabitRepository;
import com.tp1.habittracker.util.SimilarityUtils;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HabitSimilarityService {

    private final OllamaClient ollamaClient;
    private final HabitRepository habitRepository;
    private final HabitSimilarityProperties properties;

    /**
     * Finds the most similar habit to a new habit name using cosine similarity of embeddings.
     *
     * @param newHabitName the name of the new habit
     * @return an Optional containing the most similar habit if one is found above the similarity threshold,
     *         otherwise empty
     */
    public Optional<Habit> findMostSimilarHabit(String newHabitName) {
        Objects.requireNonNull(newHabitName, "newHabitName must not be null");

        if (newHabitName.isBlank()) {
            return Optional.empty();
        }

        List<Double> newHabitEmbedding = ollamaClient.generateEmbedding(newHabitName);
        return findBestMatch(newHabitEmbedding, habitRepository.findAll())
                .map(HabitSimilarityMatch::habit);
    }

    public Optional<HabitSimilarityMatch> findMostSimilarHabitForUserOrDefault(
            String userId,
            List<Double> newHabitEmbedding
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(newHabitEmbedding, "newHabitEmbedding must not be null");

        List<Habit> candidates = habitRepository.findAllByUserIdOrIsDefaultTrue(userId);
        return findBestMatch(newHabitEmbedding, candidates);
    }

    public Optional<HabitSimilarityMatch> findMostSimilarHabitForUserOrDefault(String userId, String newHabitName) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(newHabitName, "newHabitName must not be null");

        if (newHabitName.isBlank()) {
            return Optional.empty();
        }

        List<Habit> candidates = habitRepository.findAllByUserIdOrIsDefaultTrue(userId);
        Optional<HabitSimilarityMatch> deterministicMatch = findDeterministicMatch(newHabitName, candidates);

        if (deterministicMatch.isPresent()) {
            return deterministicMatch;
        }

        List<Double> newHabitEmbedding = ollamaClient.generateEmbedding(newHabitName);
        return findBestMatch(newHabitEmbedding, candidates);
    }

    public Optional<Habit> findDeterministicDuplicateForUserOrDefault(String userId, String newHabitName) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(newHabitName, "newHabitName must not be null");

        if (newHabitName.isBlank()) {
            return Optional.empty();
        }

        List<Habit> candidates = habitRepository.findAllByUserIdOrIsDefaultTrue(userId);
        return findDeterministicMatch(newHabitName, candidates).map(HabitSimilarityMatch::habit);
    }

    private Optional<HabitSimilarityMatch> findBestMatch(List<Double> newHabitEmbedding, List<Habit> candidates) {
        return candidates.stream()
                .filter(habit -> habit.getEmbedding() != null && !habit.getEmbedding().isEmpty())
                .map(habit -> computeSimilarity(newHabitEmbedding, habit))
                .flatMap(Optional::stream)
                .filter(match -> match.score() >= properties.getSimilarityThreshold())
                .max(Comparator.comparingDouble(HabitSimilarityMatch::score));
    }

    private Optional<HabitSimilarityMatch> computeSimilarity(List<Double> newHabitEmbedding, Habit candidate) {
        try {
            double similarity = SimilarityUtils.cosineSimilarity(newHabitEmbedding, candidate.getEmbedding());
            return Optional.of(new HabitSimilarityMatch(candidate, similarity));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<HabitSimilarityMatch> findDeterministicMatch(String newHabitName, List<Habit> candidates) {
        String normalizedName = normalizeName(newHabitName);

        if (normalizedName.isBlank()) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(candidate -> normalizeName(candidate.getName()).equals(normalizedName))
                .findFirst()
                .map(candidate -> new HabitSimilarityMatch(candidate, 1.0d));
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.toLowerCase(Locale.ROOT).trim().replaceAll("[^\\p{IsAlphabetic}\\s]", " ");
        StringBuilder builder = new StringBuilder();

        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }

            if (token.length() < 3) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(token);
        }

        return builder.toString();
    }

    public record HabitSimilarityMatch(Habit habit, double score) {
    }
}
