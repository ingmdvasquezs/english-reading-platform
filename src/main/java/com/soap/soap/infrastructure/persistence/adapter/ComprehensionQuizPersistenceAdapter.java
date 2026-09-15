package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionOptionEntity;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionQuestionEntity;
import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaComprehensionQuestionRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ComprehensionQuizPersistenceAdapter implements ComprehensionQuizRepositoryPort {
  private final JpaComprehensionQuestionRepository questionRepository;
  private final JpaReadingRepository readingRepository;

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

  @Override
  @Transactional
  public void replaceQuestions(UUID readingId, List<ComprehensionQuestion> questions) {
    if (readingId == null) {
      throw new IllegalArgumentException("Reading ID must not be null");
    }
    var readingEntity =
        readingRepository
            .findById(readingId)
            .orElseThrow(() -> new IllegalArgumentException("Reading not found: " + readingId));

    var existing = questionRepository.findByReadingIdOrderByOrdinalAsc(readingId);
    if (!existing.isEmpty()) {
      questionRepository.deleteAll(existing);
      questionRepository.flush();
    }

    if (questions == null || questions.isEmpty()) {
      return;
    }

    var newEntities = questions.stream().map(q -> toEntity(q, readingEntity)).toList();
    questionRepository.saveAll(newEntities);
  }

  private ComprehensionQuestionEntity toEntity(
      ComprehensionQuestion domain, ReadingEntity readingEntity) {
    var entity = new ComprehensionQuestionEntity();
    entity.setId(domain.id() != null ? domain.id() : UUID.randomUUID());
    entity.setReading(readingEntity);
    entity.setOrdinal(domain.ordinal());
    entity.setQuestionType(domain.questionType().name());
    entity.setPrompt(domain.prompt());
    entity.setExplanation(domain.explanation());
    entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now());

    if (domain.options() != null) {
      for (var opt : domain.options()) {
        var optEntity = new ComprehensionOptionEntity();
        optEntity.setId(opt.id() != null ? opt.id() : UUID.randomUUID());
        optEntity.setQuestion(entity);
        optEntity.setOrdinal(opt.ordinal());
        optEntity.setContent(opt.content());
        optEntity.setCorrect(opt.isCorrect());
        entity.getOptions().add(optEntity);
      }
    }
    return entity;
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
