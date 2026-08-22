package com.soap.soap.application.service;

import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.Word;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WordResolver {

  private final WordRepositoryPort words;

  public Word resolve(String normalizedValue, String language) {
    return words.resolve(normalizedValue, language);
  }

  public Map<String, Word> resolveAll(Collection<String> normalizedValues, String language) {
    return words.resolveAll(normalizedValues, language);
  }
}
