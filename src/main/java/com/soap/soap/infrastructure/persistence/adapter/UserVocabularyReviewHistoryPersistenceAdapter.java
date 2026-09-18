package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.model.VocabularyReviewHistoryEntry;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.infrastructure.persistence.entity.UserVocabularyReviewHistoryEntity;
import com.soap.soap.infrastructure.persistence.repository.JpaUserRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaUserVocabularyRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaUserVocabularyReviewHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserVocabularyReviewHistoryPersistenceAdapter
    implements UserVocabularyReviewHistoryRepositoryPort {

  private final JpaUserVocabularyReviewHistoryRepository historyRepository;
  private final JpaUserVocabularyRepository userVocabularyRepository;
  private final JpaUserRepository userRepository;

  @Override
  @Transactional
  public void recordReviewHistory(VocabularyReviewHistoryEntry entry) {
    var userVocabRef = userVocabularyRepository.getReferenceById(entry.userVocabularyId());
    var userRef = userRepository.getReferenceById(entry.userId());

    var entity =
        UserVocabularyReviewHistoryEntity.builder()
            .id(null)
            .userVocabulary(userVocabRef)
            .user(userRef)
            .reviewedAt(entry.reviewedAt())
            .rating(entry.rating())
            .previousSrsState(entry.previousSrsState())
            .newSrsState(entry.newSrsState())
            .previousIntervalSeconds(entry.previousIntervalSeconds())
            .newIntervalSeconds(entry.newIntervalSeconds())
            .previousStability(toBigDecimal(entry.previousStability()))
            .newStability(toBigDecimal(entry.newStability()))
            .previousDifficulty(toBigDecimal(entry.previousDifficulty()))
            .newDifficulty(toBigDecimal(entry.newDifficulty()))
            .elapsedDays(toBigDecimal(entry.elapsedDays()))
            .scheduledDays(toBigDecimal(entry.scheduledDays()))
            .build();

    historyRepository.saveAndFlush(entity);
  }

  private static BigDecimal toBigDecimal(double value) {
    return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
  }
}
