package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.Word;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface WordRepositoryPort {

  Optional<Word> findByNormalizedValueAndLanguage(String normalizedValue, String language);

  Word save(Word word);

  Word resolve(String normalizedValue, String language);

  Map<String, Word> resolveAll(Collection<String> normalizedValues, String language);

  boolean existsByNormalizedValueAndLanguage(String normalizedValue, String language);
}
