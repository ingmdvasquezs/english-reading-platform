package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "collections")
public class ReadingCollectionEntity {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 100)
  private String key;

  @Column(name = "display_name", nullable = false, length = 150)
  private String displayName;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "cover_key", length = 120)
  private String coverKey;
}
