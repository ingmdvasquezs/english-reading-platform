package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.exception.HistoricalQuizMutationException;
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
import com.soap.soap.infrastructure.persistence.repository.JpaUserComprehensionAnswerRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ComprehensionQuizPersistenceAdapter implements ComprehensionQuizRepositoryPort {
  private final JpaComprehensionQuestionRepository questionRepository;
  private final JpaReadingRepository readingRepository;
  private final JpaUserComprehensionAnswerRepository answerRepository;
  private final Clock clock;

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

    // Case 1: incoming questions is null or empty
    if (questions == null || questions.isEmpty()) {
      if (!existing.isEmpty()) {
        var existingQIds = existing.stream().map(ComprehensionQuestionEntity::getId).toList();
        var existingOptIds =
            existing.stream()
                .flatMap(q -> q.getOptions().stream().map(ComprehensionOptionEntity::getId))
                .toList();

        boolean hasHistoricalAnswers =
            (!existingQIds.isEmpty() && answerRepository.existsByQuestionIdIn(existingQIds))
                || (!existingOptIds.isEmpty()
                    && answerRepository.existsBySelectedOptionIdIn(existingOptIds));

        if (hasHistoricalAnswers) {
          throw new HistoricalQuizMutationException(
              "Cannot remove quiz questions for reading "
                  + readingId
                  + " because historical user comprehension answers reference them");
        }
        questionRepository.deleteAll(existing);
        questionRepository.flush();
      }
      return;
    }

    // Case 2: no existing questions in database -> create all incoming questions as new
    if (existing.isEmpty()) {
      var newEntities = questions.stream().map(q -> toEntity(q, readingEntity)).toList();
      questionRepository.saveAll(newEntities);
      return;
    }

    // Case 3: existing questions exist and incoming questions exist -> in-place update by ordinal
    var existingByOrdinal =
        existing.stream()
            .collect(
                Collectors.toMap(
                    ComprehensionQuestionEntity::getOrdinal, q -> q, (first, second) -> first));
    var incomingByOrdinal =
        questions.stream()
            .collect(
                Collectors.toMap(ComprehensionQuestion::ordinal, q -> q, (first, second) -> first));

    // A. Detect questions to remove (present in DB, but not in incoming)
    var questionsToRemove =
        existing.stream().filter(q -> !incomingByOrdinal.containsKey(q.getOrdinal())).toList();

    if (!questionsToRemove.isEmpty()) {
      var removingQIds =
          questionsToRemove.stream().map(ComprehensionQuestionEntity::getId).toList();
      var removingOptIds =
          questionsToRemove.stream()
              .flatMap(q -> q.getOptions().stream().map(ComprehensionOptionEntity::getId))
              .toList();

      boolean hasHistoricalAnswers =
          (!removingQIds.isEmpty() && answerRepository.existsByQuestionIdIn(removingQIds))
              || (!removingOptIds.isEmpty()
                  && answerRepository.existsBySelectedOptionIdIn(removingOptIds));

      if (hasHistoricalAnswers) {
        throw new HistoricalQuizMutationException(
            "Cannot remove questions with ordinals "
                + questionsToRemove.stream().map(ComprehensionQuestionEntity::getOrdinal).toList()
                + " because historical user comprehension answers reference them");
      }

      questionRepository.deleteAll(questionsToRemove);
      questionRepository.flush();
    }

    // B. Update questions present in both and handle their options
    var questionsToSave = new ArrayList<ComprehensionQuestionEntity>();

    for (var existingQ : existing) {
      if (!incomingByOrdinal.containsKey(existingQ.getOrdinal())) {
        continue;
      }
      var domainQ = incomingByOrdinal.get(existingQ.getOrdinal());

      existingQ.setQuestionType(domainQ.questionType().name());
      existingQ.setPrompt(domainQ.prompt());
      existingQ.setExplanation(domainQ.explanation());

      if (domainQ.options() != null) {
        var existingOptionsByOrdinal =
            existingQ.getOptions().stream()
                .collect(
                    Collectors.toMap(
                        ComprehensionOptionEntity::getOrdinal, o -> o, (first, second) -> first));

        var incomingOptionsByOrdinal =
            domainQ.options().stream()
                .collect(
                    Collectors.toMap(
                        ComprehensionOption::ordinal, o -> o, (first, second) -> first));

        // Detect options to remove in this question
        var optionsToRemove =
            existingQ.getOptions().stream()
                .filter(o -> !incomingOptionsByOrdinal.containsKey(o.getOrdinal()))
                .toList();

        if (!optionsToRemove.isEmpty()) {
          var removingOptIds =
              optionsToRemove.stream().map(ComprehensionOptionEntity::getId).toList();
          if (answerRepository.existsBySelectedOptionIdIn(removingOptIds)) {
            throw new HistoricalQuizMutationException(
                "Cannot remove options with ordinals "
                    + optionsToRemove.stream().map(ComprehensionOptionEntity::getOrdinal).toList()
                    + " from question ordinal "
                    + existingQ.getOrdinal()
                    + " because historical user comprehension answers reference them");
          }
          existingQ.getOptions().removeAll(optionsToRemove);
        }

        // If the correct option is changing, clear isCorrect on existing options and flush
        // to prevent duplicate key violations on partial unique index
        // "uk_rc_options_single_correct"
        boolean correctOptionChanged = false;
        for (var domainOpt : domainQ.options()) {
          if (existingOptionsByOrdinal.containsKey(domainOpt.ordinal())) {
            var entityOpt = existingOptionsByOrdinal.get(domainOpt.ordinal());
            if (entityOpt.isCorrect() != domainOpt.isCorrect()) {
              correctOptionChanged = true;
              break;
            }
          }
        }
        if (correctOptionChanged) {
          for (var entityOpt : existingQ.getOptions()) {
            entityOpt.setCorrect(false);
          }
          questionRepository.flush();
        }

        // Update existing options or add new options
        for (var domainOpt : domainQ.options()) {
          if (existingOptionsByOrdinal.containsKey(domainOpt.ordinal())) {
            var entityOpt = existingOptionsByOrdinal.get(domainOpt.ordinal());
            entityOpt.setContent(domainOpt.content());
            entityOpt.setCorrect(domainOpt.isCorrect());
          } else {
            var newOptEntity = new ComprehensionOptionEntity();
            newOptEntity.setId(domainOpt.id() != null ? domainOpt.id() : UUID.randomUUID());
            newOptEntity.setQuestion(existingQ);
            newOptEntity.setOrdinal(domainOpt.ordinal());
            newOptEntity.setContent(domainOpt.content());
            newOptEntity.setCorrect(domainOpt.isCorrect());
            existingQ.getOptions().add(newOptEntity);
          }
        }
      }

      questionsToSave.add(existingQ);
    }

    // C. Add completely new questions (present in incoming, but not in existing)
    for (var domainQ : questions) {
      if (!existingByOrdinal.containsKey(domainQ.ordinal())) {
        questionsToSave.add(toEntity(domainQ, readingEntity));
      }
    }

    questionRepository.saveAll(questionsToSave);
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
    entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : LocalDateTime.now(clock));

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
