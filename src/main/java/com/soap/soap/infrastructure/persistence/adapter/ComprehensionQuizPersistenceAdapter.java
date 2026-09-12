package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionQuestionEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaComprehensionQuestionRepository;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ComprehensionQuizPersistenceAdapter implements ComprehensionQuizRepositoryPort {
  private final JpaComprehensionQuestionRepository questionRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<ComprehensionQuiz> findByReadingId(UUID readingId) {
    var questionEntities = questionRepository.findByReadingIdOrderByOrdinalAsc(readingId);
    if (questionEntities.isEmpty()) {
      return Optional.empty();
    }
    var questions = questionEntities.stream().map(this::toDomainQuestion).toList();
    return Optional.of(new ComprehensionQuiz(readingId, questions));
  }

  private ComprehensionQuestion toDomainQuestion(ComprehensionQuestionEntity entity) {
    var options =
        entity.getOptions().stream()
            .sorted(Comparator.comparingInt(o -> o.getOrdinal()))
            .map(
                o ->
                    new ComprehensionOption(
                        o.getId(), entity.getId(), o.getOrdinal(), o.getContent(), o.isCorrect()))
            .toList();

    return new ComprehensionQuestion(
        entity.getId(),
        entity.getReading().getId(),
        entity.getOrdinal(),
        QuestionType.valueOf(entity.getQuestionType()),
        entity.getPrompt(),
        entity.getExplanation(),
        entity.getCreatedAt(),
        options);
  }
}
