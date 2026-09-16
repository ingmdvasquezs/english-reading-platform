package com.soap.soap.application.service;

import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingLexicalEvidence;
import com.soap.soap.application.model.RecommendationScoreV2;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PlatformReadingPersonalizationService {

  public record PersonalizationResult(
      RecommendedPlatformReading reading, RecommendationScoreV2 score) {}

  private final UserVocabularyRepositoryPort vocabulary;
  private final ReadingWordFrequencyRepositoryPort frequencyRepository;
  private final RecommendationScorerV2 scorer;
  private final RecommendationReasonEvaluator reasonEvaluator;
  private final int minGlobalClassifiedWords;

  @Autowired
  public PlatformReadingPersonalizationService(
      UserVocabularyRepositoryPort vocabulary,
      ReadingWordFrequencyRepositoryPort frequencyRepository,
      RecommendationScorerV2 scorer,
      RecommendationReasonEvaluator reasonEvaluator,
      @Value("${recommendation.v2.min-global-classified-words:30}") int minGlobalClassifiedWords) {
    this.vocabulary = vocabulary;
    this.frequencyRepository = frequencyRepository;
    this.scorer = scorer;
    this.reasonEvaluator = reasonEvaluator;
    this.minGlobalClassifiedWords = minGlobalClassifiedWords;
  }

  public PlatformReadingPersonalizationService(
      UserVocabularyRepositoryPort vocabulary,
      ReadingWordFrequencyRepositoryPort frequencyRepository,
      RecommendationScorerV2 scorer,
      RecommendationReasonEvaluator reasonEvaluator) {
    this(vocabulary, frequencyRepository, scorer, reasonEvaluator, 30);
  }

  public boolean isGlobalColdStart(UUID userId, String language) {
    if (userId == null || language == null || language.isBlank()) {
      return true;
    }
    return vocabulary.countClassifiedWordsByUserAndLanguage(userId, language)
        < minGlobalClassifiedWords;
  }

  public PersonalizationResult personalize(
      UUID readingId,
      String title,
      String language,
      EditorialLevel editorialLevel,
      String category,
      LocalDateTime createdAt,
      String coverKey,
      String shortDescription,
      ReadingProgressStatus progressStatus,
      ReadingLexicalEvidence evidence,
      boolean isGlobalColdStart) {

    RecommendationScoreV2 score = scorer.score(evidence, editorialLevel, isGlobalColdStart);

    // Personalization reason code is purely pedagogical (Recommendation V2 explanation),
    // separated from lifecycle / progressStatus (which controls CTA and progress tracking).
    RecommendationReasonCode reasonCode =
        reasonEvaluator.evaluate(
            null,
            isGlobalColdStart,
            score.insufficientEvidence(),
            score.classificationConfidence(),
            score.knownTokenCoverage(),
            score.learningUniqueRatio(),
            score.uniqueChallenge());

    int totalUnique = evidence != null ? evidence.totalUnique() : 0;
    int knownUnique = evidence != null ? evidence.knownUnique() : 0;
    int learningUnique = evidence != null ? evidence.learningUnique() : 0;
    int explicitNewUnique = evidence != null ? evidence.explicitNewUnique() : 0;
    int ignoredUnique = evidence != null ? evidence.ignoredUnique() : 0;
    int unclassifiedUnique = evidence != null ? evidence.unclassifiedUnique() : 0;

    RecommendedPlatformReading reading =
        new RecommendedPlatformReading(
            readingId,
            title,
            language,
            editorialLevel,
            category,
            createdAt,
            totalUnique,
            knownUnique,
            learningUnique,
            explicitNewUnique,
            ignoredUnique,
            unclassifiedUnique,
            score.knownComfort(),
            score.classificationConfidence(),
            progressStatus,
            coverKey,
            reasonCode,
            shortDescription);

    return new PersonalizationResult(reading, score);
  }

  public PersonalizationResult personalize(
      PlatformReadingSummary candidate,
      ReadingLexicalEvidence evidence,
      ReadingProgressStatus progressStatus,
      boolean isGlobalColdStart) {
    return personalize(
        candidate.id(),
        candidate.title(),
        candidate.language(),
        candidate.editorialLevel(),
        candidate.category(),
        candidate.createdAt(),
        candidate.coverKey(),
        candidate.shortDescription(),
        progressStatus,
        evidence,
        isGlobalColdStart);
  }

  public PersonalizationResult personalize(
      Reading reading,
      ReadingLexicalEvidence evidence,
      ReadingProgressStatus progressStatus,
      boolean isGlobalColdStart) {
    return personalize(
        reading.id(),
        reading.title(),
        reading.language() != null ? reading.language().value() : null,
        reading.editorialLevel(),
        reading.category(),
        reading.createdAt(),
        reading.coverKey(),
        reading.shortDescription(),
        progressStatus,
        evidence,
        isGlobalColdStart);
  }

  public List<RecommendedPlatformReading> personalizeReadings(
      UUID userId,
      String defaultLanguage,
      List<Reading> readings,
      Map<UUID, ReadingProgressStatus> progressByReading) {
    if (readings == null || readings.isEmpty()) {
      return List.of();
    }

    // 1. Group readings by language to batch query lexical evidence and cold start
    Map<String, List<UUID>> readingIdsByLanguage = new LinkedHashMap<>();
    for (Reading reading : readings) {
      String lang = reading.language() != null ? reading.language().value() : defaultLanguage;
      readingIdsByLanguage.computeIfAbsent(lang, ignored -> new ArrayList<>()).add(reading.id());
    }

    Map<UUID, ReadingLexicalEvidence> evidenceByReadingId = new HashMap<>();
    Map<String, Boolean> coldStartByLanguage = new HashMap<>();

    for (var entry : readingIdsByLanguage.entrySet()) {
      String lang = entry.getKey();
      List<UUID> ids = entry.getValue();

      boolean coldStart = isGlobalColdStart(userId, lang);
      coldStartByLanguage.put(lang, coldStart);

      List<ReadingLexicalEvidence> evidenceList =
          frequencyRepository.findLexicalEvidenceByUserAndLanguage(userId, lang, ids);
      for (ReadingLexicalEvidence evidence : evidenceList) {
        evidenceByReadingId.put(evidence.readingId(), evidence);
      }
    }

    // 2. Personalize each reading in the original collection order
    List<RecommendedPlatformReading> result = new ArrayList<>(readings.size());
    for (Reading reading : readings) {
      String lang = reading.language() != null ? reading.language().value() : defaultLanguage;
      boolean coldStart = coldStartByLanguage.getOrDefault(lang, true);
      ReadingLexicalEvidence evidence = evidenceByReadingId.get(reading.id());
      ReadingProgressStatus status =
          progressByReading != null ? progressByReading.get(reading.id()) : null;

      PersonalizationResult personalized = personalize(reading, evidence, status, coldStart);
      result.add(personalized.reading());
    }

    return result;
  }
}
