package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reading_collections")
public class ReadingCollectionMembershipEntity {
  @EmbeddedId private ReadingCollectionMembershipId id;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;
}
