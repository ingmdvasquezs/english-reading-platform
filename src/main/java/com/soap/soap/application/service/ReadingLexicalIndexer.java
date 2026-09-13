package com.soap.soap.application.service;

import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import java.util.LinkedHashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadingLexicalIndexer {

  private final TextWordProcessor wordProcessor;
  private final LanguageNormalizer languages;
  private final ReadingWordFrequencyRepositoryPort repository;

  @Transactional
  public void indexReading(UUID readingId, String language, String content) {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }
    if (content == null) {
      throw new IllegalArgumentException("Content must not be null");
    }
    var canonical = languages.normalize(language);
    var tokens = wordProcessor.tokenize(content);
    var frequencies = new LinkedHashMap<String, Integer>();
    for (var token : tokens) {
      frequencies.merge(token.normalizedValue(), 1, Integer::sum);
    }
    repository.replaceFrequencies(readingId, canonical, frequencies);
  }

  @Transactional
  public void removeReadingIndex(UUID readingId) {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }
    repository.deleteByReadingId(readingId);
  }
}
