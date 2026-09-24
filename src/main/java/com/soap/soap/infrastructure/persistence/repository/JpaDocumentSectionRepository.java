package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.DocumentSectionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDocumentSectionRepository extends JpaRepository<DocumentSectionEntity, UUID> {
  List<DocumentSectionEntity> findByDocumentIdOrderByOrdinalAsc(UUID documentId);

  @Query(
      value =
          """
          select s.id as id, s.ordinal as ordinal, s.title as title,
                 (select u.id from document_units u
                    where u.document_id = s.document_id and u.section_id = s.id
                    order by u.global_ordinal asc limit 1) as firstUnitId,
                 count(joined_units.id) as unitCount
          from document_sections s
          left join document_units joined_units
            on joined_units.document_id = s.document_id and joined_units.section_id = s.id
          where s.document_id = :documentId
          group by s.id, s.document_id, s.ordinal, s.title
          order by s.ordinal asc
          """,
      nativeQuery = true)
  List<SectionNavigationView> findNavigationByDocumentId(@Param("documentId") UUID documentId);

  long countByDocumentId(UUID documentId);

  void deleteByDocumentId(UUID documentId);

  interface SectionNavigationView {
    UUID getId();

    int getOrdinal();

    String getTitle();

    UUID getFirstUnitId();

    long getUnitCount();
  }
}
