package com.soap.soap.application.service;

import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.Word;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WordResolver {

  private final WordRepositoryPort words;

  public Word resolve(String normalizedValue, String language) {
    return words
        .findByNormalizedValueAndLanguage(normalizedValue, language)
        .orElseGet(() -> words.save(new Word(null, normalizedValue, language)));
  }
}
