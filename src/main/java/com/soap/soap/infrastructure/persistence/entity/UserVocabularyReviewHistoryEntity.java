package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_vocabulary_review_history")
public class UserVocabularyReviewHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_vocabulary_id", nullable = false)
  private UserVocabularyEntity userVocabulary;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false)
  private LocalDateTime reviewedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReviewRating rating;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_srs_state", nullable = false, length = 20)
  private SrsState previousSrsState;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_srs_state", nullable = false, length = 20)
  private SrsState newSrsState;

  @Column(name = "previous_interval_seconds", nullable = false)
  private long previousIntervalSeconds;

  @Column(name = "new_interval_seconds", nullable = false)
  private long newIntervalSeconds;

  @Column(name = "previous_stability", nullable = false, precision = 10, scale = 4)
  private BigDecimal previousStability;

  @Column(name = "new_stability", nullable = false, precision = 10, scale = 4)
  private BigDecimal newStability;

  @Column(name = "previous_difficulty", nullable = false, precision = 10, scale = 4)
  private BigDecimal previousDifficulty;

  @Column(name = "new_difficulty", nullable = false, precision = 10, scale = 4)
  private BigDecimal newDifficulty;

  @Column(name = "elapsed_days", nullable = false, precision = 10, scale = 4)
  private BigDecimal elapsedDays;

  @Column(name = "scheduled_days", nullable = false, precision = 10, scale = 4)
  private BigDecimal scheduledDays;
}
