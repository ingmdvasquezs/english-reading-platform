package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.VocabularyReviewSession;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface VocabularyReviewSessionRepositoryPort {
  Optional<VocabularyReviewSession> findSession(UUID userId, LocalDate localDate);

  Optional<VocabularyReviewSession> findSessionForUpdate(UUID userId, LocalDate localDate);

  void acquireSessionCreationLock(UUID userId);

  VocabularyReviewSession saveSession(VocabularyReviewSession session);
}
