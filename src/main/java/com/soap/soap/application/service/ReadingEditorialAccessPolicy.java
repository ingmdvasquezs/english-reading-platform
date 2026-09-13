package com.soap.soap.application.service;

import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReadingEditorialAccessPolicy {

  private final ReadingProgressRepositoryPort progressRepository;

  public boolean canAccess(Reading reading, UUID userId) {
    return canAccess(reading, userId, null);
  }

  public boolean canAccess(Reading reading, UUID userId, Boolean knownHasProgress) {
    Objects.requireNonNull(reading, "Reading must not be null");
    Objects.requireNonNull(userId, "UserId must not be null");

    if (reading.origin() == ReadingOrigin.USER) {
      return reading.user() != null && userId.equals(reading.user().id());
    }

    if (reading.origin() == ReadingOrigin.PLATFORM) {
      EditorialStatus status = reading.editorialStatus();
      if (status == EditorialStatus.PUBLISHED) {
        return true;
      }
      if (status == EditorialStatus.ARCHIVED) {
        if (knownHasProgress != null) {
          return knownHasProgress;
        }
        return progressRepository.findByUserIdAndReadingId(userId, reading.id()).isPresent();
      }
      if (status == EditorialStatus.DRAFT) {
        return false;
      }
    }

    return false;
  }

  public void requireAccessible(Reading reading, UUID userId) {
    requireAccessible(reading, userId, null);
  }

  public void requireAccessible(Reading reading, UUID userId, Boolean knownHasProgress) {
    if (!canAccess(reading, userId, knownHasProgress)) {
      throw new ReadingNotFoundException(reading.id());
    }
  }
}
