package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.ComprehensionNotAvailableException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.LatestComprehensionResult;
import com.soap.soap.application.port.in.GetLatestComprehensionResultPort;
import com.soap.soap.application.port.out.ComprehensionAttemptRepositoryPort;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.ReadingOrigin;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetLatestComprehensionResultUseCase implements GetLatestComprehensionResultPort {
  private final CurrentUserPort currentUser;
  private final ReadingRepositoryPort readings;
  private final ComprehensionQuizRepositoryPort quizRepository;
  private final ComprehensionAttemptRepositoryPort attemptRepository;

  @Override
  @Transactional(readOnly = true)
  public LatestComprehensionResult getLatestResult(UUID readingId) {
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
          "Comprehension is only available for platform readings");
    }

    var latestOpt = attemptRepository.findLatestByUserIdAndReadingId(userId, readingId);
    if (latestOpt.isEmpty()) {
      return new LatestComprehensionResult(readingId, false, null);
    }

    var quiz =
        quizRepository
            .findByReadingId(readingId)
            .orElseThrow(() -> new IllegalStateException("Quiz not found for recorded attempt"));

    var attemptResult = SubmitComprehensionAttemptUseCase.toResult(latestOpt.get(), quiz);
    return new LatestComprehensionResult(readingId, true, attemptResult);
  }
}
