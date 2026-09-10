package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.port.in.ListUserReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.VocabularyCompatibilityCalculator;
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
public class ListUserReadingsUseCase implements ListUserReadingsPort {
  private final UserRepositoryPort users;
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final UserVocabularyRepositoryPort vocabulary;
  private final TextWordProcessor wordProcessor;
  private final VocabularyCompatibilityCalculator compatibilityCalculator;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public PageResult<ReadingSummary> listUserReadings(PageRequest pageRequest) {
    var userId = currentUser.requireUserId();
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    var page = readings.findUserReadingsByUserId(userId, pageRequest);
    var progressByReading =
        progress.findByUserIdAndReadingIds(
            userId,
            page.content().stream().map(reading -> reading.id()).collect(Collectors.toSet()));
    var wordsByReading = new LinkedHashMap<UUID, Set<String>>();
    var wordsByLanguage = new HashMap<String, Set<String>>();
    for (var reading : page.content()) {
      var uniqueWords =
          wordProcessor.tokenize(reading.content()).stream()
              .map(TextWordProcessor.Token::normalizedValue)
              .collect(Collectors.toUnmodifiableSet());
      wordsByReading.put(reading.id(), uniqueWords);
      wordsByLanguage
          .computeIfAbsent(reading.language(), ignored -> new HashSet<>())
          .addAll(uniqueWords);
    }
    var statusesByLanguage = new HashMap<String, Map<String, VocabularyStatus>>();
    wordsByLanguage.forEach(
        (language, words) ->
            statusesByLanguage.put(
                language,
                words.isEmpty()
                    ? Map.of()
                    : vocabulary.findStatusesByNormalizedValues(userId, language, words)));
    var summaries =
        page.content().stream()
            .map(
                reading -> {
                  var compatibility =
                      compatibilityCalculator.calculate(
                          wordsByReading.get(reading.id()),
                          statusesByLanguage.getOrDefault(reading.language(), Map.of()));
                  var breakdown = compatibility.breakdown();
                  return new ReadingSummary(
                      reading.id(),
                      reading.title(),
                      reading.language(),
                      reading.createdAt(),
                      breakdown.uniqueWords(),
                      breakdown.knownWords(),
                      breakdown.learningWords(),
                      breakdown.explicitNewWords(),
                      breakdown.ignoredWords(),
                      breakdown.unclassifiedWords(),
                      compatibility.vocabularyFitPercentage(),
                      compatibility.classificationConfidencePercentage(),
                      progressByReading.containsKey(reading.id())
                          ? progressByReading.get(reading.id()).status()
                          : null);
                })
            .toList();
    return new PageResult<>(summaries, page.page(), page.size(), page.totalElements());
  }
}
