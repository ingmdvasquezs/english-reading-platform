package com.soap.soap.infrastructure.persistence.entity;

import com.soap.soap.domain.model.DocumentUnitKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document_units")
public class DocumentUnitEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "section_id")
  private UUID sectionId;

  @Column(name = "global_ordinal", nullable = false)
  private int globalOrdinal;

  @Column(name = "section_ordinal", nullable = false)
  private int sectionOrdinal;

  @Enumerated(EnumType.STRING)
  @Column(name = "unit_kind", nullable = false, length = 30)
  private DocumentUnitKind kind;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "word_count", nullable = false)
  private int wordCount;

  @Column(name = "source_locator", length = 1000)
  private String sourceLocator;

  @Column(name = "content_hash", columnDefinition = "char(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String contentHash;
}
