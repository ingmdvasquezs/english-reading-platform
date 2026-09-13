package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.ComprehensionNotAvailableException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.ComprehensionQuizOptionView;
import com.soap.soap.application.model.ComprehensionQuizQuestionView;
import com.soap.soap.application.model.ComprehensionQuizView;
import com.soap.soap.application.port.in.GetReadingComprehensionQuizPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.service.ReadingEditorialAccessPolicy;
import com.soap.soap.domain.model.ComprehensionQuestion;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.service.ComprehensionQuizSelectionPolicy;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetReadingComprehensionQuizUseCase implements GetReadingComprehensionQuizPort {
  private final CurrentUserPort currentUser;
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final ComprehensionQuizRepositoryPort quizRepository;
  private final ComprehensionQuizSelectionPolicy selectionPolicy;
  private final ReadingEditorialAccessPolicy accessPolicy;

  @Override
  @Transactional(readOnly = true)
  public ComprehensionQuizView getQuiz(UUID readingId, UUID submissionId) {
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    var userId = currentUser.requireUserId();
    var reading =
        readings.findById(readingId).orElseThrow(() -> new ReadingNotFoundException(readingId));

    if (reading.origin() != ReadingOrigin.PLATFORM) {
      accessPolicy.requireAccessible(reading, userId);
      throw new ComprehensionNotAvailableException(
          "Comprehension quiz is only available for platform readings");
    }

    var readingProgress = progress.findByUserIdAndReadingId(userId, readingId);
    accessPolicy.requireAccessible(reading, userId, readingProgress.isPresent());

    if (readingProgress.isEmpty()
        || readingProgress.get().status() != ReadingProgressStatus.COMPLETED) {
      throw new ComprehensionNotAvailableException(
          "Comprehension quiz requires the reading to be completed");
    }

    var quizOpt = quizRepository.findByReadingId(readingId);
    if (quizOpt.isEmpty() || !quizOpt.get().isAvailable()) {
      return new ComprehensionQuizView(readingId, false, List.of(), null);
    }

    var quiz = quizOpt.get();

    List<ComprehensionQuestion> selectedQuestions;
    Integer selectionVersion = null;

    if (submissionId == null) {
      // Legacy path (submissionId omitted): return canonical V1 questions up to ordinal 3
      selectedQuestions =
          quiz.questions().stream()
              .filter(q -> q.ordinal() <= 3)
              .sorted(Comparator.comparingInt(ComprehensionQuestion::ordinal))
              .toList();
    } else {
      // Versioned selection path
      selectionVersion = ComprehensionQuizSelectionPolicy.CURRENT_SELECTION_VERSION;
      selectedQuestions =
          selectionPolicy.select(quiz, userId, readingId, submissionId, selectionVersion);
    }

    var questionsView =
        selectedQuestions.stream()
            .map(
                q ->
                    new ComprehensionQuizQuestionView(
                        q.id(),
                        q.ordinal(),
                        q.questionType(),
                        q.prompt(),
                        q.options().stream()
                            .sorted(Comparator.comparingInt(o -> o.ordinal()))
                            .map(
                                o ->
                                    new ComprehensionQuizOptionView(
                                        o.id(), o.ordinal(), o.content()))
                            .toList()))
            .toList();

    return new ComprehensionQuizView(readingId, true, questionsView, selectionVersion);
  }
}
