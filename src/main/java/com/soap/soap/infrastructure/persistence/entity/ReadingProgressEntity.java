package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.ReadingProgressStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "reading_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "reading_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ReadingProgressEntity {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reading_id", nullable = false)
  private ReadingEntity reading;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReadingProgressStatus status;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "current_part_ordinal")
  private Integer currentPartOrdinal;

  @Column(name = "pagination_version")
  private Integer paginationVersion;
}
