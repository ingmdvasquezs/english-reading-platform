package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.AnswerSubmission;
import com.soap.soap.application.command.SubmitComprehensionAttemptCommand;
import com.soap.soap.application.exception.ComprehensionNotAvailableException;
import com.soap.soap.application.exception.InvalidComprehensionSubmissionException;
import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.UserComprehensionAnswer;
import com.soap.soap.domain.model.UserComprehensionAttempt;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitComprehensionAttemptUseCaseTest {

  @Mock private CurrentUserPort currentUser;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private ComprehensionQuizRepositoryPort quizRepository;
  @Mock private ComprehensionAttemptRepositoryPort attemptRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T10:00:00Z"), ZoneId.of("UTC"));
  private SubmitComprehensionAttemptUseCase useCase;

  private UUID userId;
  private UUID readingId;
  private UUID submissionId;
  private Reading platformReading;
  private ComprehensionQuiz quiz;
  private UUID q1Id;
  private UUID q1OptCorrect;
  private UUID q1OptWrong;
  private UUID q2Id;
  private UUID q2OptCorrect;
  private UUID q2OptWrong;

  @BeforeEach
  void setUp() {
    useCase =
        new SubmitComprehensionAttemptUseCase(
            currentUser, readings, progress, quizRepository, attemptRepository, clock);

    userId = UUID.randomUUID();
    readingId = UUID.randomUUID();
    submissionId = UUID.randomUUID();

    platformReading =
        new Reading(
            readingId,
            null,
            "Platform Reading",
            "Content",
            "en",
            LocalDateTime.now(clock),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Education");

    q1Id = UUID.randomUUID();
    q1OptCorrect = UUID.randomUUID();
    q1OptWrong = UUID.randomUUID();
    var q1Options =
        List.of(
            new ComprehensionOption(q1OptCorrect, q1Id, 1, "Correct Opt", true),
            new ComprehensionOption(q1OptWrong, q1Id, 2, "Wrong Opt 1", false),
            new ComprehensionOption(UUID.randomUUID(), q1Id, 3, "Wrong Opt 2", false),
            new ComprehensionOption(UUID.randomUUID(), q1Id, 4, "Wrong Opt 3", false));
    var q1 =
        new ComprehensionQuestion(
            q1Id,
            readingId,
            1,
            QuestionType.FACTUAL,
            "Question 1?",
            "Explanation 1",
            LocalDateTime.now(clock),
            q1Options);

    q2Id = UUID.randomUUID();
    q2OptCorrect = UUID.randomUUID();
    q2OptWrong = UUID.randomUUID();
    var q2Options =
        List.of(
            new ComprehensionOption(q2OptCorrect, q2Id, 1, "Correct Opt 2", true),
            new ComprehensionOption(q2OptWrong, q2Id, 2, "Wrong Opt 2.1", false),
            new ComprehensionOption(UUID.randomUUID(), q2Id, 3, "Wrong Opt 2.2", false),
            new ComprehensionOption(UUID.randomUUID(), q2Id, 4, "Wrong Opt 2.3", false));
    var q2 =
        new ComprehensionQuestion(
            q2Id,
            readingId,
            2,
            QuestionType.INFERENCE,
            "Question 2?",
            "Explanation 2",
            LocalDateTime.now(clock),
            q2Options);

    quiz = new ComprehensionQuiz(readingId, List.of(q1, q2));
  }

  @Test
  void evaluatesAndPersistsNewAttemptCorrectly() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(attemptRepository.findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId))
        .thenReturn(Optional.empty());
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.completed(
                    userId,
                    readingId,
                    LocalDateTime.now(clock).minusMinutes(10),
                    LocalDateTime.now(clock))));
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(quiz));

    when(attemptRepository.recordAttempt(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Answer 1 correct, answer 2 wrong: 1/2 = 50.00%
    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect), new AnswerSubmission(q2Id, q2OptWrong)));

    var result = useCase.submitAttempt(command);

    assertThat(result.scorePercentage()).isEqualTo(new BigDecimal("50.00"));
    assertThat(result.correctAnswersCount()).isEqualTo(1);
    assertThat(result.totalQuestionsCount()).isEqualTo(2);
    assertThat(result.submissionId()).isEqualTo(submissionId);
    assertThat(result.readingId()).isEqualTo(readingId);
    assertThat(result.questions()).hasSize(2);

    var q1Res = result.questions().get(0);
    assertThat(q1Res.isCorrect()).isTrue();
    assertThat(q1Res.selectedOptionId()).isEqualTo(q1OptCorrect);
    assertThat(q1Res.correctOptionId()).isEqualTo(q1OptCorrect);
    assertThat(q1Res.explanation()).isEqualTo("Explanation 1");

    var q2Res = result.questions().get(1);
    assertThat(q2Res.isCorrect()).isFalse();
    assertThat(q2Res.selectedOptionId()).isEqualTo(q2OptWrong);
    assertThat(q2Res.correctOptionId()).isEqualTo(q2OptCorrect);
    assertThat(q2Res.explanation()).isEqualTo("Explanation 2");

    verify(attemptRepository).recordAttempt(any(), any());
  }

  @Test
  void returnsExistingAttemptWhenSameSubmissionIdIsRetried() {
    UUID attemptId = UUID.randomUUID();
    var existingAttempt =
        new UserComprehensionAttempt(
            attemptId,
            userId,
            readingId,
            submissionId,
            new BigDecimal("100.00"),
            2,
            2,
            LocalDateTime.now(clock),
            List.of(
                new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q1Id, q1OptCorrect, true),
                new UserComprehensionAnswer(
                    UUID.randomUUID(), attemptId, q2Id, q2OptCorrect, true)));

    when(currentUser.requireUserId()).thenReturn(userId);
    when(attemptRepository.findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId))
        .thenReturn(Optional.of(existingAttempt));
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(quiz));

    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect)));

    var result = useCase.submitAttempt(command);

    assertThat(result.attemptId()).isEqualTo(attemptId);
    assertThat(result.scorePercentage()).isEqualTo(new BigDecimal("100.00"));
    assertThat(result.correctAnswersCount()).isEqualTo(2);

    verify(attemptRepository, never()).recordAttempt(any(), any());
  }

  @Test
  void throwsExceptionWhenDuplicateAnswersAreSubmitted() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(attemptRepository.findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId))
        .thenReturn(Optional.empty());
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.completed(
                    userId,
                    readingId,
                    LocalDateTime.now(clock).minusMinutes(10),
                    LocalDateTime.now(clock))));
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(quiz));

    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect), new AnswerSubmission(q1Id, q1OptWrong)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Duplicate answers");
  }

  @Test
  void throwsExceptionWhenAnswersCountDoesNotMatchQuizQuestions() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(attemptRepository.findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId))
        .thenReturn(Optional.empty());
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.completed(
                    userId,
                    readingId,
                    LocalDateTime.now(clock).minusMinutes(10),
                    LocalDateTime.now(clock))));
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(quiz));

    // Only 1 answer provided for 2 questions
    var command =
        new SubmitComprehensionAttemptCommand(
            readingId, submissionId, List.of(new AnswerSubmission(q1Id, q1OptCorrect)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Answers count does not match");
  }

  @Test
  void throwsExceptionWhenSelectedOptionDoesNotBelongToQuestion() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(attemptRepository.findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId))
        .thenReturn(Optional.empty());
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.completed(
                    userId,
                    readingId,
                    LocalDateTime.now(clock).minusMinutes(10),
                    LocalDateTime.now(clock))));
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(quiz));

    // Passing q2's option for q1
    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q2OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Selected option does not belong to question");
  }

  @Test
  void throwsExceptionWhenReadingIsNotCompleted() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(attemptRepository.findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId))
        .thenReturn(Optional.empty());
    when(readings.findById(readingId)).thenReturn(Optional.of(platformReading));
    when(progress.findByUserIdAndReadingId(userId, readingId))
        .thenReturn(
            Optional.of(
                ReadingProgress.inProgress(
                    userId, readingId, LocalDateTime.now(clock).minusMinutes(10))));

    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(ComprehensionNotAvailableException.class)
        .hasMessageContaining("requires the reading to be completed");
  }
}
