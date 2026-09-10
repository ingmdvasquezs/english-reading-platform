package com.soap.soap.application.usecase;

import com.soap.soap.application.command.UpdateReadingProgressCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.in.UpdateReadingProgressPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateReadingProgressUseCase implements UpdateReadingProgressPort {
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  @Override
  @Transactional
  public ReadingProgress updateReadingProgress(UpdateReadingProgressCommand command) {
    validate(command);
    var userId = currentUser.requireUserId();
    readings
        .findById(command.readingId())
        .filter(reading -> reading.isAccessibleBy(userId))
        .orElseThrow(() -> new ReadingNotFoundException(command.readingId()));

    var now = LocalDateTime.now(clock);
    var updated =
        command.progressStatus() == ReadingProgressStatus.COMPLETED
            ? progress.complete(userId, command.readingId(), now)
            : progress.startIfAbsent(userId, command.readingId(), now);
    if (command.currentPartOrdinal() != null) {
      updated =
          progress.updatePosition(
              userId,
              command.readingId(),
              command.currentPartOrdinal(),
              command.paginationVersion());
    }
    return updated;
  }

  private void validate(UpdateReadingProgressCommand command) {
    if (command == null || command.readingId() == null || command.progressStatus() == null) {
      throw new InvalidApplicationArgumentException("Reading id and progress status are required");
    }
    if ((command.currentPartOrdinal() == null) != (command.paginationVersion() == null)) {
      throw new InvalidApplicationArgumentException(
          "Current part ordinal and pagination version must be provided together");
    }
    if (command.currentPartOrdinal() != null
        && (command.currentPartOrdinal() < 1 || command.paginationVersion() < 1)) {
      throw new InvalidApplicationArgumentException(
          "Current part ordinal and pagination version must be positive");
    }
  }
}
