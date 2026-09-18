package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.VocabularyStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "user_vocabulary",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_vocabulary_user_word",
          columnNames = {"user_id", "word_id"})
    })
public class UserVocabularyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "word_id", nullable = false)
  private WordEntity word;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VocabularyStatus status;

  @Column(nullable = false)
  private LocalDateTime firstSeenAt;

  private LocalDateTime learnedAt;

  @Column(nullable = false)
  private int reviewStage;

  private LocalDateTime lastReviewedAt;

  private LocalDateTime nextReviewAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "srs_state", nullable = false, length = 20)
  private com.soap.soap.domain.model.SrsState srsState;

  @Column(nullable = false, precision = 10, scale = 4)
  private java.math.BigDecimal stability;

  @Column(nullable = false, precision = 10, scale = 4)
  private java.math.BigDecimal difficulty;

  @Column(nullable = false)
  private int repetitions;

  @Column(nullable = false)
  private int lapses;

  @Version private Long version;
}
