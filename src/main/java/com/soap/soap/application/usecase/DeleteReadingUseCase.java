package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.in.DeleteReadingPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.ReadingOrigin;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteReadingUseCase implements DeleteReadingPort {
  private final ReadingRepositoryPort readings;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional
  public void deleteReading(UUID readingId) {
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    var userId = currentUser.requireUserId();
    readings
        .findById(readingId)
        .filter(reading -> reading.origin() == ReadingOrigin.USER)
        .filter(reading -> reading.user() != null && reading.user().id().equals(userId))
        .orElseThrow(() -> new ReadingNotFoundException(readingId));

    readings.deleteById(readingId);
    log.info("User reading deleted: readingId={}, userId={}", readingId, userId);
  }
}
