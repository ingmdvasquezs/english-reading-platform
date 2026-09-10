package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.DocumentProgressStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document_progress")
public class DocumentProgressEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "current_unit_id", nullable = false)
  private UUID currentUnitId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentProgressStatus status;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "last_read_at", nullable = false)
  private LocalDateTime lastReadAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Version private Long version;
}
