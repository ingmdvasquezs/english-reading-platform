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

  @Override
  @Transactional(readOnly = true)
  public java.util.List<java.util.UUID> findDistinctUserVocabularyIdsReviewedBetween(
      java.util.UUID userId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
    return historyRepository.findDistinctUserVocabularyIdsReviewedBetween(userId, start, end);
  }

  @Override
  @Transactional(readOnly = true)
  public long countDistinctReviewedWordsBetween(
      java.util.UUID userId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
    return historyRepository.countDistinctReviewedWordsBetween(userId, start, end);
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.List<com.soap.soap.application.model.ReviewedWordDaySummary>
      findReviewedWordsSummaryBetween(
          java.util.UUID userId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
    return historyRepository.findReviewedWordsSummaryBetween(userId, start, end).stream()
        .map(
            row ->
                new com.soap.soap.application.model.ReviewedWordDaySummary(
                    (java.util.UUID) row[0],
                    (java.time.LocalDateTime) row[1],
                    (java.time.LocalDateTime) row[2]))
        .toList();
  }

  private static BigDecimal toBigDecimal(double value) {
    return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
  }
}
