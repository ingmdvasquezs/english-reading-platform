package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.domain.model.UserComprehensionAnswer;
import com.soap.soap.domain.model.UserComprehensionAttempt;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionOptionEntity;
import com.soap.soap.infrastructure.persistence.entity.ComprehensionQuestionEntity;
import com.soap.soap.infrastructure.persistence.entity.UserComprehensionAnswerEntity;
import com.soap.soap.infrastructure.persistence.entity.UserComprehensionAttemptEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaUserComprehensionAnswerRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaUserComprehensionAttemptRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ComprehensionAttemptPersistenceAdapter implements ComprehensionAttemptRepositoryPort {
  private final JpaUserComprehensionAttemptRepository attemptRepository;
  private final JpaUserComprehensionAnswerRepository answerRepository;
  private final EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserComprehensionAttempt> findByUserIdAndReadingIdAndSubmissionId(
      UUID userId, UUID readingId, UUID submissionId) {
    return attemptRepository
        .findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId)
        .map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserComprehensionAttempt> findLatestByUserIdAndReadingId(
      UUID userId, UUID readingId) {
    return attemptRepository
        .findFirstByUserIdAndReadingIdOrderBySubmittedAtDescIdDesc(userId, readingId)
        .map(this::toDomain);
  }

  @Override
  @Transactional
  public UserComprehensionAttempt recordAttempt(
      UserComprehensionAttempt attempt, List<UserComprehensionAnswer> answers) {
    int inserted =
        attemptRepository.insertAttemptIfAbsent(
            attempt.id(),
            attempt.userId(),
            attempt.readingId(),
            attempt.submissionId(),
            attempt.scorePercentage(),
            attempt.correctAnswersCount(),
            attempt.totalQuestionsCount(),
            attempt.submittedAt());

    if (inserted == 1) {
      var attemptRef =
          entityManager.getReference(UserComprehensionAttemptEntity.class, attempt.id());
      for (var ans : answers) {
        var questionRef =
            entityManager.getReference(ComprehensionQuestionEntity.class, ans.questionId());
        var optionRef =
            entityManager.getReference(ComprehensionOptionEntity.class, ans.selectedOptionId());
        var entity = new UserComprehensionAnswerEntity();
        entity.setId(ans.id());
        entity.setAttempt(attemptRef);
        entity.setQuestion(questionRef);
        entity.setSelectedOption(optionRef);
        entity.setCorrect(ans.isCorrect());
        answerRepository.save(entity);
      }
      return attempt;
    } else {
      return findByUserIdAndReadingIdAndSubmissionId(
              attempt.userId(), attempt.readingId(), attempt.submissionId())
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Concurrent attempt was recorded but could not be retrieved"));
    }
  }

  private UserComprehensionAttempt toDomain(UserComprehensionAttemptEntity entity) {
    var domainAnswers =
        entity.getAnswers().stream()
            .map(
                a ->
                    new UserComprehensionAnswer(
                        a.getId(),
                        entity.getId(),
                        a.getQuestion().getId(),
                        a.getSelectedOption().getId(),
                        a.isCorrect()))
            .toList();

    return new UserComprehensionAttempt(
        entity.getId(),
        entity.getUser().getId(),
        entity.getReading().getId(),
        entity.getSubmissionId(),
        entity.getScorePercentage(),
        entity.getCorrectAnswersCount(),
        entity.getTotalQuestionsCount(),
        entity.getSubmittedAt(),
        domainAnswers);
  }
}
