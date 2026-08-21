package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.in.GetReadingPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.Reading;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetReadingUseCase implements GetReadingPort {
  private final ReadingRepositoryPort readings;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public Reading getReading(UUID readingId) {
    if (readingId == null) {
      throw new InvalidApplicationArgumentException("Reading id must not be null");
    }
    var userId = currentUser.requireUserId();
    return readings
        .findById(readingId)
        .filter(r -> userId.equals(r.user().id()))
        .orElseThrow(() -> new ReadingNotFoundException(readingId));
  }
}
