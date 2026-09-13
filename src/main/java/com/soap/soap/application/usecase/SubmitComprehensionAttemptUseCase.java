package com.soap.soap.application.usecase;

import com.soap.soap.application.command.AnswerSubmission;
import com.soap.soap.application.command.SubmitComprehensionAttemptCommand;
import com.soap.soap.application.exception.ComprehensionNotAvailableException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.InvalidComprehensionSubmissionException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.ComprehensionAttemptResult;
import com.soap.soap.application.model.ComprehensionOptionResult;
import com.soap.soap.application.model.ComprehensionQuestionResult;
import com.soap.soap.application.port.in.SubmitComprehensionAttemptPort;
import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.service.ReadingEditorialAccessPolicy;
import com.soap.soap.domain.model.ComprehensionOption;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ComprehensionQuiz;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.UserComprehensionAnswer;
import com.soap.soap.domain.model.UserComprehensionAttempt;
import com.soap.soap.domain.service.ComprehensionQuizSelectionPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SubmitComprehensionAttemptUseCase implements SubmitComprehensionAttemptPort {
  private final CurrentUserPort currentUser;
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final ComprehensionQuizRepositoryPort quizRepository;
  private final ComprehensionAttemptRepositoryPort attemptRepository;
  private final ComprehensionQuizSelectionPolicy selectionPolicy;
  private final ReadingEditorialAccessPolicy accessPolicy;
  private final Clock clock;

  @Override
  @Transactional
  public ComprehensionAttemptResult submitAttempt(SubmitComprehensionAttemptCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Command must not be null");
    }
    if (command.readingId() == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    if (command.submissionId() == null) {
      throw new InvalidApplicationArgumentException("Submission id must not be null");
    }
    if (command.answers() == null || command.answers().isEmpty()) {
      throw new InvalidComprehensionSubmissionException("Answers list must not be empty");
    }

    var userId = currentUser.requireUserId();

    var existingOpt =
        attemptRepository.findByUserIdAndReadingIdAndSubmissionId(
            userId, command.readingId(), command.submissionId());
    if (existingOpt.isPresent()) {
      var quiz =
          quizRepository
              .findByReadingId(command.readingId())
              .orElseThrow(() -> new IllegalStateException("Quiz not found for existing attempt"));
      return toResult(existingOpt.get(), quiz);
    }

    int requestedVersion =
        (command.selectionVersion() != null)
            ? command.selectionVersion()
            : ComprehensionQuizSelectionPolicy.SELECTION_VERSION_1;

    var reading =
        readings
            .findById(command.readingId())
            .orElseThrow(() -> new ReadingNotFoundException(command.readingId()));

    if (reading.origin() != ReadingOrigin.PLATFORM) {
      accessPolicy.requireAccessible(reading, userId);
      throw new ComprehensionNotAvailableException(
          "Comprehension quiz is only available for platform readings");
    }

    var readingProgress = progress.findByUserIdAndReadingId(userId, command.readingId());
    accessPolicy.requireAccessible(reading, userId, readingProgress.isPresent());

    if (readingProgress.isEmpty()
        || readingProgress.get().status() != ReadingProgressStatus.COMPLETED) {
      throw new ComprehensionNotAvailableException(
          "Comprehension quiz requires the reading to be completed");
    }

    var quiz =
        quizRepository
            .findByReadingId(command.readingId())
            .filter(ComprehensionQuiz::isAvailable)
            .orElseThrow(
                () ->
                    new ComprehensionNotAvailableException(
                        "Comprehension quiz is not available for this reading"));

    List<ComprehensionQuestion> assignedQuestions;
    try {
      assignedQuestions =
          selectionPolicy.select(
              quiz, userId, command.readingId(), command.submissionId(), requestedVersion);
    } catch (IllegalArgumentException ex) {
      throw new InvalidComprehensionSubmissionException(
          "Invalid quiz selection: " + ex.getMessage());
    }

    var distinctQuestions =
        command.answers().stream().map(AnswerSubmission::questionId).collect(Collectors.toSet());
    if (distinctQuestions.size() != command.answers().size()) {
      throw new InvalidComprehensionSubmissionException(
          "Duplicate answers submitted for the same question");
    }
    if (command.answers().size() != 3) {
      throw new InvalidComprehensionSubmissionException("Answers count must be exactly 3");
    }

    Set<UUID> assignedQuestionIds =
        assignedQuestions.stream().map(ComprehensionQuestion::id).collect(Collectors.toSet());
    if (!distinctQuestions.equals(assignedQuestionIds)) {
      throw new InvalidComprehensionSubmissionException(
          "Submitted questions do not match the assigned quiz set");
    }

    Map<UUID, ComprehensionQuestion> assignedMap =
        assignedQuestions.stream()
            .collect(Collectors.toMap(ComprehensionQuestion::id, Function.identity()));

    UUID attemptId = UUID.randomUUID();
    int correctCount = 0;
    List<UserComprehensionAnswer> domainAnswers = new ArrayList<>();

    for (var answer : command.answers()) {
      if (answer.questionId() == null || answer.selectedOptionId() == null) {
        throw new InvalidComprehensionSubmissionException(
            "Question id and selected option id must not be null");
      }
      var question = assignedMap.get(answer.questionId());
      if (question == null) {
        throw new InvalidComprehensionSubmissionException(
            "Question does not belong to this reading quiz");
      }
      var selectedOption =
          question.options().stream()
              .filter(o -> o.id().equals(answer.selectedOptionId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new InvalidComprehensionSubmissionException(
                          "Selected option does not belong to question"));

      boolean isCorrect = selectedOption.isCorrect();
      if (isCorrect) {
        correctCount++;
      }
      domainAnswers.add(
          new UserComprehensionAnswer(
              UUID.randomUUID(), attemptId, question.id(), selectedOption.id(), isCorrect));
    }

    int totalCount = 3;
    BigDecimal scorePercentage =
        BigDecimal.valueOf(correctCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);

    var attempt =
        new UserComprehensionAttempt(
            attemptId,
            userId,
            command.readingId(),
            command.submissionId(),
            scorePercentage,
            correctCount,
            totalCount,
            LocalDateTime.now(clock),
            domainAnswers);

    var recorded = attemptRepository.recordAttempt(attempt, domainAnswers);
    return toResult(recorded, quiz);
  }

  static ComprehensionAttemptResult toResult(
      UserComprehensionAttempt attempt, ComprehensionQuiz quiz) {
    Map<UUID, UserComprehensionAnswer> answerMap =
        attempt.answers().stream()
            .collect(Collectors.toMap(UserComprehensionAnswer::questionId, Function.identity()));

    Map<UUID, ComprehensionQuestion> questionsById =
        quiz.questions().stream()
            .collect(Collectors.toMap(ComprehensionQuestion::id, Function.identity()));

    List<ComprehensionQuestion> answeredQuestions =
        attempt.answers().stream()
            .map(UserComprehensionAnswer::questionId)
            .map(questionsById::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(q -> displayOrderForType(q.questionType())))
            .toList();

    List<ComprehensionQuestionResult> questionResults = new ArrayList<>();
    int displayOrdinal = 1;

    for (var q : answeredQuestions) {
      var ans = answerMap.get(q.id());
      UUID selectedOptionId = ans != null ? ans.selectedOptionId() : null;
      boolean isCorrect = ans != null && ans.isCorrect();
      UUID correctOptionId =
          q.options().stream()
              .filter(ComprehensionOption::isCorrect)
              .map(ComprehensionOption::id)
              .findFirst()
              .orElse(null);

      var options =
          q.options().stream()
              .sorted(Comparator.comparingInt(ComprehensionOption::ordinal))
              .map(o -> new ComprehensionOptionResult(o.id(), o.ordinal(), o.content()))
              .toList();

      questionResults.add(
          new ComprehensionQuestionResult(
              q.id(),
              displayOrdinal++,
              q.questionType(),
              q.prompt(),
              selectedOptionId,
              correctOptionId,
              isCorrect,
              q.explanation(),
              options));
    }

    return new ComprehensionAttemptResult(
        attempt.id(),
        attempt.readingId(),
        attempt.submissionId(),
        attempt.scorePercentage(),
        attempt.correctAnswersCount(),
        attempt.totalQuestionsCount(),
        attempt.submittedAt(),
        questionResults);
  }

  private static int displayOrderForType(QuestionType type) {
    return switch (type) {
      case FACTUAL -> 1;
      case INFERENCE -> 2;
      case MAIN_IDEA -> 3;
    };
  }
}
