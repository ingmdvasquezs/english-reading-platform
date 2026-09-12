package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.UserComprehensionAnswer;
import com.soap.soap.domain.model.UserComprehensionAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComprehensionAttemptRepositoryPort {
  Optional<UserComprehensionAttempt> findByUserIdAndReadingIdAndSubmissionId(
      UUID userId, UUID readingId, UUID submissionId);

  Optional<UserComprehensionAttempt> findLatestByUserIdAndReadingId(UUID userId, UUID readingId);

  UserComprehensionAttempt recordAttempt(
      UserComprehensionAttempt attempt, List<UserComprehensionAnswer> answers);
}
