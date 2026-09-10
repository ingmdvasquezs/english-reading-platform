package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.InitialVocabularyTest;
import com.soap.soap.application.port.out.InitialVocabularyTestSourcePort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.infrastructure.persistence.mapper.OnboardingReadingEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaOnboardingReadingRepository;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OnboardingReadingPersistenceAdapter implements InitialVocabularyTestSourcePort {
  private final JpaOnboardingReadingRepository repository;
  private final OnboardingReadingEntityMapper mapper;
  private final TextWordProcessor processor;

  @Override
  @Transactional(readOnly = true)
  public InitialVocabularyTest load() {
    var reading =
        repository
            .findFirstByActiveTrueOrderByVersionDescCreatedAtDescIdDesc()
            .map(mapper::toDomain)
            .orElseThrow(
                () -> new IllegalStateException("No active onboarding reading configured"));
    var words = new LinkedHashSet<String>();
    processor.tokenize(reading.content()).forEach(token -> words.add(token.normalizedValue()));
    return new InitialVocabularyTest(
        "onboarding-reading-%d-v%d".formatted(reading.id(), reading.version()),
        reading.content(),
        words.stream().toList());
  }
}
