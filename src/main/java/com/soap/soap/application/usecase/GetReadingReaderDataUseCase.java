package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.ReaderToken;
import com.soap.soap.application.model.ReadingReaderData;
import com.soap.soap.application.port.in.GetReadingReaderDataPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.ReaderTextTokenizer;
import com.soap.soap.domain.model.VocabularyStatus;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetReadingReaderDataUseCase implements GetReadingReaderDataPort {
  private final ReadingRepositoryPort readings;
  private final UserVocabularyRepositoryPort vocabulary;
  private final ReaderTextTokenizer tokenizer;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public ReadingReaderData getReadingReaderData(UUID readingId) {
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    var userId = currentUser.requireUserId();
    var reading =
        readings
            .findById(readingId)
            .filter(candidate -> userId.equals(candidate.user().id()))
            .orElseThrow(() -> new ReadingNotFoundException(readingId));

    var tokens = tokenizer.tokenize(reading.content());
    var normalizedValues =
        tokens.stream()
            .map(ReaderToken::normalizedValue)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    var statuses =
        normalizedValues.isEmpty()
            ? Map.<String, VocabularyStatus>of()
            : vocabulary.findStatusesByNormalizedValues(
                userId, reading.language(), normalizedValues);
    var classifiedTokens =
        tokens.stream()
            .map(
                token ->
                    token.normalizedValue() == null
                        ? token
                        : new ReaderToken(
                            token.value(),
                            token.normalizedValue(),
                            token.type(),
                            statuses.getOrDefault(token.normalizedValue(), VocabularyStatus.NEW)))
            .toList();
    return new ReadingReaderData(
        reading.id(), reading.title(), reading.language(), classifiedTokens);
  }
}
