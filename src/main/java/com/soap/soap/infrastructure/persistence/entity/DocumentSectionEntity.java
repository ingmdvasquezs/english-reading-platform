package com.soap.soap.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "document_sections")
public class DocumentSectionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private int ordinal;

  @Column(length = 500)
  private String title;

  @Column(name = "source_locator", length = 1000)
  private String sourceLocator;
}
