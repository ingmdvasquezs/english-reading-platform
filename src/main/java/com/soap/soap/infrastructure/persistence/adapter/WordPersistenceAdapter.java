package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.persistence.mapper.WordEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaWordRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WordPersistenceAdapter implements WordRepositoryPort {

  private final JpaWordRepository repository;
  private final WordEntityMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Optional<Word> findByNormalizedValueAndLanguage(String normalizedValue, String language) {
    return repository
        .findByNormalizedValueAndLanguage(normalizedValue, language)
        .map(mapper::toDomain);
  }

  @Override
  @Transactional
  public Word save(Word word) {
    var entity = mapper.toEntity(word);
    var savedEntity = repository.save(entity);
    return mapper.toDomain(savedEntity);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByNormalizedValueAndLanguage(String normalizedValue, String language) {
    return repository.existsByNormalizedValueAndLanguage(normalizedValue, language);
  }
}
