package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.port.in.AnalyzeReadingPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.ReadingAnalyzer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.ReadingAnalysis;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AnalyzeReadingUseCase implements AnalyzeReadingPort {
  private final UserRepositoryPort users;
  private final ReadingRepositoryPort readings;
  private final UserVocabularyRepositoryPort vocabulary;
  private final TextWordProcessor wordProcessor;
  private final ReadingAnalyzer analyzer;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public ReadingAnalysis analyzeReading(UUID readingId) {
    var userId = currentUser.requireUserId();
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    var reading =
        readings.findById(readingId).orElseThrow(() -> new ReadingNotFoundException(readingId));
    if (!userId.equals(reading.user().id())) {
      throw new ReadingNotFoundException(readingId);
    }

    var tokens = wordProcessor.tokenize(reading.content());
    var distinctValues =
        tokens.stream().map(TextWordProcessor.Token::normalizedValue).collect(Collectors.toSet());
    var statuses =
        distinctValues.isEmpty()
            ? Map.<String, com.soap.soap.domain.model.VocabularyStatus>of()
            : vocabulary.findStatusesByNormalizedValues(userId, reading.language(), distinctValues);
    return analyzer.analyze(tokens, statuses);
  }
}
