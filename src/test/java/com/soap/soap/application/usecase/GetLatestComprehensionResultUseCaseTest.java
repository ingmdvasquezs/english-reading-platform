package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.ComprehensionNotAvailableException;
import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserComprehensionAnswer;
import com.soap.soap.domain.model.UserComprehensionAttempt;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetLatestComprehensionResultUseCaseTest {

  @Mock private CurrentUserPort currentUser;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ComprehensionQuizRepositoryPort quizRepository;
  @Mock private ComprehensionAttemptRepositoryPort attemptRepository;
  @Mock private com.soap.soap.application.service.ReadingEditorialAccessPolicy accessPolicy;

  @InjectMocks private GetLatestComprehensionResultUseCase useCase;

  private UUID userId;
  private UUID readingId;
  private Reading platformReading;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    readingId = UUID.randomUUID();
    platformReading =
        new Reading(
            readingId,
            null,
            "Platform Reading",
            "Content",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Education",
            com.soap.soap.domain.model.EditorialStatus.PUBLISHED);
  }

  @Test
  void returnsLatestAttemptWhenPresent() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));

    UUID qId = UUID.randomUUID();
    UUID optId = UUID.randomUUID();
    var q =
        new ComprehensionQuestion(
            qId,
            readingId,
            1,
            QuestionType.FACTUAL,
            "Prompt?",
            "Exp",
            LocalDateTime.now(),
            List.of(
                new ComprehensionOption(optId, qId, 1, "O1", true),
                new ComprehensionOption(UUID.randomUUID(), qId, 2, "O2", false),
                new ComprehensionOption(UUID.randomUUID(), qId, 3, "O3", false),
                new ComprehensionOption(UUID.randomUUID(), qId, 4, "O4", false)));
    var quiz = new ComprehensionQuiz(readingId, List.of(q));
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(quiz));

    UUID attemptId = UUID.randomUUID();
    var attempt =
        new UserComprehensionAttempt(
            attemptId,
            userId,
            readingId,
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            1,
            1,
            LocalDateTime.now(),
            List.of(new UserComprehensionAnswer(UUID.randomUUID(), attemptId, qId, optId, true)));
    when(attemptRepository.findLatestByUserIdAndReadingId(userId, readingId))
        .thenReturn(Optional.of(attempt));

    var result = useCase.getLatestResult(readingId);

    assertThat(result.hasAttempt()).isTrue();
    assertThat(result.attempt()).isNotNull();
    assertThat(result.attempt().attemptId()).isEqualTo(attemptId);
    assertThat(result.attempt().scorePercentage()).isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void returnsHasAttemptFalseWhenUserHasNoAttempts() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(attemptRepository.findLatestByUserIdAndReadingId(userId, readingId))
        .thenReturn(Optional.empty());

    var result = useCase.getLatestResult(readingId);

    assertThat(result.hasAttempt()).isFalse();
    assertThat(result.attempt()).isNull();
    verifyNoInteractions(quizRepository);
  }

  @Test
  void throwsExceptionWhenReadingOriginIsNotPlatform() {
    var userReading =
        new Reading(
            readingId,
            new User(userId, "User", "u@example.com", "hash", null),
            "My Reading",
            "User content",
            "en",
            LocalDateTime.now());
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(readingId)).thenReturn(Optional.of(userReading));

    assertThatThrownBy(() -> useCase.getLatestResult(readingId))
        .isInstanceOf(ComprehensionNotAvailableException.class)
        .hasMessageContaining("only available for platform readings");

    verifyNoInteractions(attemptRepository, quizRepository);
  }
}
