package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "vocabulary_review_session_items")
public class VocabularyReviewSessionItemEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false)
  private VocabularyReviewSessionEntity session;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_vocabulary_id", nullable = false)
  private UserVocabularyEntity userVocabulary;

  @Column(name = "base_order", nullable = false)
  private int baseOrder;

  @Column(name = "introduced_at")
  private LocalDateTime introducedAt;

  @Column(name = "pending_queue_sequence")
  private Long pendingQueueSequence;
}
