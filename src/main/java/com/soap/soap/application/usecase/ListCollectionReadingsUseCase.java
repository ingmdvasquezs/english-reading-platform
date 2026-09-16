package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.CollectionNotFoundException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.PlatformReadingRecommendationCalculator;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListCollectionReadingsUseCase implements ListCollectionReadingsPort {
  private final UserRepositoryPort users;
  private final ReadingCollectionRepositoryPort collections;
  private final ReadingProgressRepositoryPort progress;
  private final UserVocabularyRepositoryPort vocabulary;
  private final TextWordProcessor wordProcessor;
  private final PlatformReadingRecommendationCalculator calculator;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public PageResult<RecommendedPlatformReading> listCollectionReadings(
      String collectionKey, PageRequest pageRequest) {
    var userId = currentUser.requireUserId();
    if (collectionKey == null || collectionKey.isBlank()) {
      throw new InvalidApplicationArgumentException("Collection key must not be blank");
    }
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    // Verify collection exists
    collections
        .findActiveByKey(collectionKey)
        .orElseThrow(() -> new CollectionNotFoundException(collectionKey));

    // Load user and determine learning language
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (user.learningLanguage() == null || user.learningLanguage().isBlank()) {
      throw new IllegalStateException(
          "User " + userId + " does not have an active learning language configured");
    }
    String learningLanguage = LanguageTag.of(user.learningLanguage()).value();

    // Fetch readings scoped to user language
    var page = collections.findReadings(collectionKey, learningLanguage, pageRequest);
    var wordsByReading = new LinkedHashMap<UUID, Set<String>>();
    var wordsByLanguage = new HashMap<String, Set<String>>();
    for (var reading : page.content()) {
      var uniqueWords =
          wordProcessor.tokenize(reading.content()).stream()
              .map(TextWordProcessor.Token::normalizedValue)
              .collect(Collectors.toUnmodifiableSet());
      wordsByReading.put(reading.id(), uniqueWords);
      String readingLang = reading.language() == null ? null : reading.language().value();
      wordsByLanguage.computeIfAbsent(readingLang, ignored -> new HashSet<>()).addAll(uniqueWords);
    }
    var statusesByLanguage = new HashMap<String, Map<String, VocabularyStatus>>();
    wordsByLanguage.forEach(
        (language, words) ->
            statusesByLanguage.put(
                language,
                words.isEmpty()
                    ? Map.of()
                    : vocabulary.findStatusesByNormalizedValues(userId, language, words)));
    var progressByReading =
        progress.findByUserIdAndReadingIds(
            userId,
            page.content().stream().map(reading -> reading.id()).collect(Collectors.toSet()));
    var summaries =
        page.content().stream()
            .map(
                reading -> {
                  String readingLang =
                      reading.language() == null ? null : reading.language().value();
                  var calculated =
                      calculator.calculate(
                          reading,
                          wordsByReading.get(reading.id()),
                          statusesByLanguage.getOrDefault(readingLang, Map.of()));
                  return withProgress(
                      calculated,
                      progressByReading.containsKey(reading.id())
                          ? progressByReading.get(reading.id()).status()
                          : null);
                })
            .toList();
    return new PageResult<>(summaries, page.page(), page.size(), page.totalElements());
  }

  private static RecommendedPlatformReading withProgress(
      RecommendedPlatformReading reading,
      com.soap.soap.domain.model.ReadingProgressStatus progressStatus) {
    return new RecommendedPlatformReading(
        reading.readingId(),
        reading.title(),
        reading.language(),
        reading.editorialLevel(),
        reading.category(),
        reading.createdAt(),
        reading.uniqueWords(),
        reading.knownWords(),
        reading.learningWords(),
        reading.explicitNewWords(),
        reading.ignoredWords(),
        reading.unclassifiedWords(),
        reading.vocabularyFitPercentage(),
        reading.classificationConfidencePercentage(),
        progressStatus,
        reading.coverKey(),
        reading.reasonCode(),
        reading.shortDescription());
  }
}
