package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.ReviewSessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vocabulary_review_sessions")
public class VocabularyReviewSessionEntity implements Persistable<UUID> {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "local_review_date", nullable = false)
  private LocalDate localReviewDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReviewSessionStatus status;

  @Column(name = "daily_limit", nullable = false)
  private int dailyLimit;

  @Column(name = "next_queue_sequence", nullable = false)
  private long nextQueueSequence;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("baseOrder ASC")
  @Builder.Default
  private List<VocabularyReviewSessionItemEntity> items = new ArrayList<>();

  @Transient @Builder.Default private boolean isNew = true;

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  public void addItem(VocabularyReviewSessionItemEntity item) {
    items.add(item);
    item.setSession(this);
  }
}
