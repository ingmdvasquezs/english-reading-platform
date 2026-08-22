package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.persistence.mapper.WordEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaWordRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WordPersistenceAdapter implements WordRepositoryPort {

  private final JpaWordRepository repository;
  private final WordEntityMapper mapper;
  private final JdbcTemplate jdbc;

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
  @Transactional
  public Word resolve(String normalizedValue, String language) {
    return jdbc.queryForObject(
        """
        INSERT INTO words (id, normalized_value, language, created_at)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (normalized_value, language)
        DO UPDATE SET normalized_value = EXCLUDED.normalized_value
        RETURNING id, normalized_value, language, created_at
        """,
        (result, row) ->
            new Word(
                result.getObject("id", UUID.class),
                result.getString("normalized_value"),
                result.getString("language")),
        UUID.randomUUID(),
        normalizedValue,
        language);
  }

  @Override
  @Transactional
  public Map<String, Word> resolveAll(Collection<String> normalizedValues, String language) {
    if (normalizedValues.isEmpty()) {
      return Map.of();
    }
    jdbc.batchUpdate(
        """
        INSERT INTO words (id, normalized_value, language, created_at)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (normalized_value, language) DO NOTHING
        """,
        normalizedValues,
        100,
        (statement, normalizedValue) -> {
          statement.setObject(1, UUID.randomUUID());
          statement.setString(2, normalizedValue);
          statement.setString(3, language);
        });
    var resolved = new LinkedHashMap<String, Word>();
    repository
        .findByLanguageAndNormalizedValueIn(language, normalizedValues)
        .forEach(entity -> resolved.put(entity.getNormalizedValue(), mapper.toDomain(entity)));
    return Map.copyOf(resolved);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByNormalizedValueAndLanguage(String normalizedValue, String language) {
    return repository.existsByNormalizedValueAndLanguage(normalizedValue, language);
  }
}
