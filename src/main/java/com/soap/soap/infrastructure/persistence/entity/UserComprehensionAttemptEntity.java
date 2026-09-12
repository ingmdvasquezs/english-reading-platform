package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "user_comprehension_attempts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "reading_id", "submission_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserComprehensionAttemptEntity {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reading_id", nullable = false)
  private ReadingEntity reading;

  @Column(name = "submission_id", nullable = false)
  private UUID submissionId;

  @Column(name = "score_percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal scorePercentage;

  @Column(name = "correct_answers_count", nullable = false)
  private int correctAnswersCount;

  @Column(name = "total_questions_count", nullable = false)
  private int totalQuestionsCount;

  @Column(name = "submitted_at", nullable = false)
  private LocalDateTime submittedAt;

  @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<UserComprehensionAnswerEntity> answers = new ArrayList<>();
}
