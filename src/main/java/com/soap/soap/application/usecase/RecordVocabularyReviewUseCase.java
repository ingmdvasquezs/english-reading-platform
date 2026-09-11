package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.port.in.RecordVocabularyReviewPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.UserVocabulary;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordVocabularyReviewUseCase implements RecordVocabularyReviewPort {
  private final CurrentUserPort currentUser;
  private final UserVocabularyRepositoryPort vocabulary;
  private final Clock clock;

  @Override
  @Transactional
  public UserVocabulary recordReview(UUID wordId, ReviewAssessment assessment) {
    if (wordId == null) {
      throw new InvalidApplicationArgumentException("Word ID must not be null");
    }
    if (assessment == null) {
      throw new InvalidApplicationArgumentException("Review assessment must not be null");
    }
    var userId = currentUser.requireUserId();
    var current =
        vocabulary
            .findByUserIdAndWordId(userId, wordId)
            .orElseThrow(() -> new VocabularyEntryNotFoundException(userId, wordId));
    var updated = current.applyReviewAssessment(assessment, clock);
    return vocabulary.save(updated);
  }
}
