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
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
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

  @Override
  @Transactional(readOnly = true)
  public ComprehensionQuizView getQuiz(UUID readingId) {
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    var userId = currentUser.requireUserId();
    var reading =
        readings
            .findById(readingId)
            .filter(candidate -> candidate.isAccessibleBy(userId))
            .orElseThrow(() -> new ReadingNotFoundException(readingId));

    if (reading.origin() != ReadingOrigin.PLATFORM) {
      throw new ComprehensionNotAvailableException(
          "Comprehension quiz is only available for platform readings");
    }

    var readingProgress = progress.findByUserIdAndReadingId(userId, readingId);
    if (readingProgress.isEmpty()
        || readingProgress.get().status() != ReadingProgressStatus.COMPLETED) {
      throw new ComprehensionNotAvailableException(
          "Comprehension quiz requires the reading to be completed");
    }

    var quizOpt = quizRepository.findByReadingId(readingId);
    if (quizOpt.isEmpty() || !quizOpt.get().isAvailable()) {
      return new ComprehensionQuizView(readingId, false, List.of());
    }

    var quiz = quizOpt.get();
    var questionsView =
        quiz.questions().stream()
            .sorted(Comparator.comparingInt(q -> q.ordinal()))
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

    return new ComprehensionQuizView(readingId, true, questionsView);
  }
}
