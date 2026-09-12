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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "reading_comprehension_questions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"reading_id", "ordinal"}))
@Getter
@Setter
@NoArgsConstructor
public class ComprehensionQuestionEntity {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reading_id", nullable = false)
  private ReadingEntity reading;

  @Column(nullable = false)
  private int ordinal;

  @Column(name = "question_type", nullable = false, length = 30)
  private String questionType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String prompt;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String explanation;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("ordinal ASC")
  private List<ComprehensionOptionEntity> options = new ArrayList<>();
}
