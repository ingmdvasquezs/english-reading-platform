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
import com.soap.soap.domain.service.ComprehensionQuizSelectionPolicy;
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

  private final ComprehensionQuizSelectionPolicy selectionPolicy =
      new ComprehensionQuizSelectionPolicy();
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
  private UUID q3Id;
  private UUID q3OptCorrect;
  private UUID q3OptWrong;

  @BeforeEach
  void setUp() {
    useCase =
        new SubmitComprehensionAttemptUseCase(
            currentUser,
            readings,
            progress,
            quizRepository,
            attemptRepository,
            selectionPolicy,
            clock);

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
            new ComprehensionOption(q1OptCorrect, q1Id, 1, "Correct Opt 1", true),
            new ComprehensionOption(q1OptWrong, q1Id, 2, "Wrong Opt 1.1", false),
            new ComprehensionOption(UUID.randomUUID(), q1Id, 3, "Wrong Opt 1.2", false),
            new ComprehensionOption(UUID.randomUUID(), q1Id, 4, "Wrong Opt 1.3", false));
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

    q3Id = UUID.randomUUID();
    q3OptCorrect = UUID.randomUUID();
    q3OptWrong = UUID.randomUUID();
    var q3Options =
        List.of(
            new ComprehensionOption(q3OptCorrect, q3Id, 1, "Correct Opt 3", true),
            new ComprehensionOption(q3OptWrong, q3Id, 2, "Wrong Opt 3.1", false),
            new ComprehensionOption(UUID.randomUUID(), q3Id, 3, "Wrong Opt 3.2", false),
            new ComprehensionOption(UUID.randomUUID(), q3Id, 4, "Wrong Opt 3.3", false));
    var q3 =
        new ComprehensionQuestion(
            q3Id,
            readingId,
            3,
            QuestionType.MAIN_IDEA,
            "Question 3?",
            "Explanation 3",
            LocalDateTime.now(clock),
            q3Options);

    quiz = new ComprehensionQuiz(readingId, List.of(q1, q2, q3));
  }

  private List<ComprehensionOption> createFourOptions(UUID qId) {
    return List.of(
        new ComprehensionOption(UUID.randomUUID(), qId, 1, "Opt 1", true),
        new ComprehensionOption(UUID.randomUUID(), qId, 2, "Opt 2", false),
        new ComprehensionOption(UUID.randomUUID(), qId, 3, "Opt 3", false),
        new ComprehensionOption(UUID.randomUUID(), qId, 4, "Opt 4", false));
  }

  @Test
  void evaluatesAndPersistsNewAttemptCorrectlyWithLegacyCommand() {
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

    // Answer 1 correct, answer 2 wrong, answer 3 correct: 2/3 = 66.67%
    // Legacy command without selectionVersion
    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptWrong),
                new AnswerSubmission(q3Id, q3OptCorrect)));

    var result = useCase.submitAttempt(command);

    assertThat(result.scorePercentage()).isEqualTo(new BigDecimal("66.67"));
    assertThat(result.correctAnswersCount()).isEqualTo(2);
    assertThat(result.totalQuestionsCount()).isEqualTo(3);
    assertThat(result.submissionId()).isEqualTo(submissionId);
    assertThat(result.readingId()).isEqualTo(readingId);
    assertThat(result.questions()).hasSize(3);

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

    var q3Res = result.questions().get(2);
    assertThat(q3Res.isCorrect()).isTrue();
    assertThat(q3Res.selectedOptionId()).isEqualTo(q3OptCorrect);
    assertThat(q3Res.correctOptionId()).isEqualTo(q3OptCorrect);

    verify(attemptRepository).recordAttempt(any(), any());
  }

  @Test
  void evaluatesAndPersistsWithExplicitSelectionVersion1() {
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

    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(q3Id, q3OptCorrect)),
            1);

    var result = useCase.submitAttempt(command);

    assertThat(result.scorePercentage()).isEqualTo(new BigDecimal("100.00"));
    assertThat(result.correctAnswersCount()).isEqualTo(3);
    assertThat(result.totalQuestionsCount()).isEqualTo(3);
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
            3,
            3,
            LocalDateTime.now(clock),
            List.of(
                new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q1Id, q1OptCorrect, true),
                new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q2Id, q2OptCorrect, true),
                new UserComprehensionAnswer(
                    UUID.randomUUID(), attemptId, q3Id, q3OptCorrect, true)));

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
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(q3Id, q3OptCorrect)));

    var result = useCase.submitAttempt(command);

    assertThat(result.attemptId()).isEqualTo(attemptId);
    assertThat(result.scorePercentage()).isEqualTo(new BigDecimal("100.00"));
    assertThat(result.correctAnswersCount()).isEqualTo(3);

    verify(attemptRepository, never()).recordAttempt(any(), any());
  }

  @Test
  void reconstructsResultOnlyFromAttemptAnswersWhenBankHasExtraQuestions() {
    // Bank contains 6 questions (e.g. Future V26)
    UUID q4Id = UUID.randomUUID();
    UUID q5Id = UUID.randomUUID();
    UUID q6Id = UUID.randomUUID();

    var q4 =
        new ComprehensionQuestion(
            q4Id,
            readingId,
            4,
            QuestionType.FACTUAL,
            "Q4",
            "E4",
            LocalDateTime.now(clock),
            createFourOptions(q4Id));
    var q5 =
        new ComprehensionQuestion(
            q5Id,
            readingId,
            5,
            QuestionType.INFERENCE,
            "Q5",
            "E5",
            LocalDateTime.now(clock),
            createFourOptions(q5Id));
    var q6 =
        new ComprehensionQuestion(
            q6Id,
            readingId,
            6,
            QuestionType.MAIN_IDEA,
            "Q6",
            "E6",
            LocalDateTime.now(clock),
            createFourOptions(q6Id));

    var bankWith6 =
        new ComprehensionQuiz(
            readingId,
            List.of(
                quiz.questions().get(0),
                quiz.questions().get(1),
                quiz.questions().get(2),
                q4,
                q5,
                q6));

    UUID attemptId = UUID.randomUUID();
    var historicalAttempt =
        new UserComprehensionAttempt(
            attemptId,
            userId,
            readingId,
            submissionId,
            new BigDecimal("100.00"),
            3,
            3,
            LocalDateTime.now(clock),
            List.of(
                new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q1Id, q1OptCorrect, true),
                new UserComprehensionAnswer(UUID.randomUUID(), attemptId, q2Id, q2OptCorrect, true),
                new UserComprehensionAnswer(
                    UUID.randomUUID(), attemptId, q3Id, q3OptCorrect, true)));

    when(currentUser.requireUserId()).thenReturn(userId);
    when(attemptRepository.findByUserIdAndReadingIdAndSubmissionId(userId, readingId, submissionId))
        .thenReturn(Optional.of(historicalAttempt));
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(bankWith6));

    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(q3Id, q3OptCorrect)));

    var result = useCase.submitAttempt(command);

    assertThat(result.questions()).hasSize(3);
    assertThat(result.questions().stream().map(q -> q.questionId()).toList())
        .containsExactly(q1Id, q2Id, q3Id);
    assertThat(result.questions().get(0).ordinal()).isEqualTo(1);
    assertThat(result.questions().get(1).ordinal()).isEqualTo(2);
    assertThat(result.questions().get(2).ordinal()).isEqualTo(3);
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
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q1Id, q1OptWrong),
                new AnswerSubmission(q2Id, q2OptCorrect)));

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

    // Only 2 answers provided for 3 questions
    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Answers count must be exactly 3");
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
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(q3Id, q3OptCorrect)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Selected option does not belong to question");
  }

  @Test
  void throwsExceptionWhenQuestionDoesNotBelongToAssignedSet() {
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

    UUID foreignQuestionId = UUID.randomUUID();
    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(foreignQuestionId, UUID.randomUUID())));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Submitted questions do not match the assigned quiz set");
  }

  @Test
  void throwsExceptionWhenSubmittingTwoQuestionsOfSameType() {
    // A bank with 4 questions (two FACTUAL)
    UUID q4FactualId = UUID.randomUUID();
    UUID q4OptCorrect = UUID.randomUUID();
    var q4 =
        new ComprehensionQuestion(
            q4FactualId,
            readingId,
            4,
            QuestionType.FACTUAL,
            "Question 4 (FACTUAL)?",
            "Explanation 4",
            LocalDateTime.now(clock),
            List.of(
                new ComprehensionOption(q4OptCorrect, q4FactualId, 1, "Opt 1", true),
                new ComprehensionOption(UUID.randomUUID(), q4FactualId, 2, "Opt 2", false),
                new ComprehensionOption(UUID.randomUUID(), q4FactualId, 3, "Opt 3", false),
                new ComprehensionOption(UUID.randomUUID(), q4FactualId, 4, "Opt 4", false)));

    var quizWith4 =
        new ComprehensionQuiz(
            readingId,
            List.of(quiz.questions().get(0), quiz.questions().get(1), quiz.questions().get(2), q4));

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
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(quizWith4));

    // Client submits two FACTUAL questions (q1 and q4) instead of the assigned trio
    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q4FactualId, q4OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Submitted questions do not match the assigned quiz set");
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
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(q3Id, q3OptCorrect)));

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(ComprehensionNotAvailableException.class)
        .hasMessageContaining("requires the reading to be completed");
  }

  @Test
  void throwsExceptionWhenUnsupportedSelectionVersionProvided() {
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
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(q3Id, q3OptCorrect)),
            99);

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(InvalidComprehensionSubmissionException.class)
        .hasMessageContaining("Unsupported selectionVersion: 99");
  }

  @Test
  void throwsIllegalStateExceptionWhenBankIsMisconfigured() {
    // Bank missing MAIN_IDEA
    var incompleteQuiz =
        new ComprehensionQuiz(readingId, List.of(quiz.questions().get(0), quiz.questions().get(1)));

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
    when(quizRepository.findByReadingId(readingId)).thenReturn(Optional.of(incompleteQuiz));

    var command =
        new SubmitComprehensionAttemptCommand(
            readingId,
            submissionId,
            List.of(
                new AnswerSubmission(q1Id, q1OptCorrect),
                new AnswerSubmission(q2Id, q2OptCorrect),
                new AnswerSubmission(q3Id, q3OptCorrect)),
            1);

    assertThatThrownBy(() -> useCase.submitAttempt(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No candidate questions found for type MAIN_IDEA");
  }
}
