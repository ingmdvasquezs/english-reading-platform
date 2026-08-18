package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class CreatedAtEntity {

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void assignCreatedAt() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
