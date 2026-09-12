package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserComprehensionAttemptEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserComprehensionAttemptRepository
    extends JpaRepository<UserComprehensionAttemptEntity, UUID> {

  Optional<UserComprehensionAttemptEntity> findByUserIdAndReadingIdAndSubmissionId(
      UUID userId, UUID readingId, UUID submissionId);

  Optional<UserComprehensionAttemptEntity>
      findFirstByUserIdAndReadingIdOrderBySubmittedAtDescIdDesc(UUID userId, UUID readingId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO user_comprehension_attempts(
              id, user_id, reading_id, submission_id, score_percentage,
              correct_answers_count, total_questions_count, submitted_at)
          VALUES (
              :id, :userId, :readingId, :submissionId, :scorePercentage,
              :correctAnswersCount, :totalQuestionsCount, :submittedAt)
          ON CONFLICT (user_id, reading_id, submission_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertAttemptIfAbsent(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("readingId") UUID readingId,
      @Param("submissionId") UUID submissionId,
      @Param("scorePercentage") BigDecimal scorePercentage,
      @Param("correctAnswersCount") int correctAnswersCount,
      @Param("totalQuestionsCount") int totalQuestionsCount,
      @Param("submittedAt") LocalDateTime submittedAt);
}
