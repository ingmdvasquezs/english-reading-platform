package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "reading_comprehension_options",
    uniqueConstraints = @UniqueConstraint(columnNames = {"question_id", "ordinal"}))
@Getter
@Setter
@NoArgsConstructor
public class ComprehensionOptionEntity {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private ComprehensionQuestionEntity question;

  @Column(nullable = false)
  private int ordinal;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "is_correct", nullable = false)
  private boolean isCorrect;
}
