package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.Word;
import java.util.Optional;

public interface WordRepositoryPort {

  Optional<Word> findByNormalizedValueAndLanguage(String normalizedValue, String language);

  Word save(Word word);

  boolean existsByNormalizedValueAndLanguage(String normalizedValue, String language);
}
