package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.ReadingReaderData;
import com.soap.soap.application.port.in.GetReadingReaderDataPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.service.ReaderContentPreparer;
import com.soap.soap.application.service.ReadingEditorialAccessPolicy;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetReadingReaderDataUseCase implements GetReadingReaderDataPort {
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final ReaderContentPreparer readerContent;
  private final ReadingEditorialAccessPolicy accessPolicy;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  @Override
  @Transactional
  public ReadingReaderData getReadingReaderData(UUID readingId) {
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    var userId = currentUser.requireUserId();
    var reading =
        readings.findById(readingId).orElseThrow(() -> new ReadingNotFoundException(readingId));

    ReadingProgress readingProgress;
    if (reading.origin() == ReadingOrigin.PLATFORM
        && reading.editorialStatus() == EditorialStatus.ARCHIVED) {
      var existingProgress = progress.findByUserIdAndReadingId(userId, reading.id());
      accessPolicy.requireAccessible(reading, userId, existingProgress.isPresent());
      readingProgress = existingProgress.get();
    } else {
      accessPolicy.requireAccessible(reading, userId);
      readingProgress = progress.startIfAbsent(userId, reading.id(), LocalDateTime.now(clock));
    }

    var classifiedTokens =
        readerContent.prepare(
            userId,
            reading.language() == null ? null : reading.language().value(),
            reading.content());
    return new ReadingReaderData(
        reading.id(),
        reading.title(),
        reading.language() == null ? null : reading.language().value(),
        classifiedTokens,
        readingProgress.status(),
        readingProgress.currentPartOrdinal(),
        readingProgress.paginationVersion());
  }
}
