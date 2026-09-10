package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.in.CompleteReadingPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.ReadingProgress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CompleteReadingUseCase implements CompleteReadingPort {
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  @Override
  @Transactional
  public ReadingProgress completeReading(UUID readingId) {
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    var userId = currentUser.requireUserId();
    readings
        .findById(readingId)
        .filter(candidate -> candidate.isAccessibleBy(userId))
        .orElseThrow(() -> new ReadingNotFoundException(readingId));
    return progress.complete(userId, readingId, LocalDateTime.now(clock));
  }
}
