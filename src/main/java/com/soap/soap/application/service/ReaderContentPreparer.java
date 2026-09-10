package com.soap.soap.application.service;

import com.soap.soap.application.model.ReaderToken;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReaderContentPreparer {
  private final ReaderTextTokenizer tokenizer;
  private final UserVocabularyRepositoryPort vocabulary;

  public java.util.List<ReaderToken> prepare(UUID userId, String language, String content) {
    var tokens = tokenizer.tokenize(content);
    var words =
        tokens.stream()
            .map(ReaderToken::normalizedValue)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    var statuses =
        words.isEmpty()
            ? Map.<String, com.soap.soap.domain.model.VocabularyStatus>of()
            : vocabulary.findStatusesByNormalizedValues(userId, language, words);
    return tokens.stream()
        .map(
            token ->
                token.normalizedValue() == null
                    ? token
                    : new ReaderToken(
                        token.value(),
                        token.normalizedValue(),
                        token.type(),
                        statuses.get(token.normalizedValue())))
        .toList();
  }
}
