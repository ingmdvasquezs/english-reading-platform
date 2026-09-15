package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionMembershipEntity;
import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionMembershipId;
import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReadingCollectionMembershipRepository
    extends JpaRepository<ReadingCollectionMembershipEntity, ReadingCollectionMembershipId> {

  @Query(
      value =
          """
          select r
          from ReadingCollectionMembershipEntity membership
          join ReadingCollectionEntity collection on collection.id = membership.id.collectionId
          join ReadingEntity r on r.id = membership.id.readingId
          where collection.key = :key and collection.active = true
            and r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
          order by membership.displayOrder, r.id
          """,
      countQuery =
          """
          select count(membership)
          from ReadingCollectionMembershipEntity membership
          join ReadingCollectionEntity collection on collection.id = membership.id.collectionId
          join ReadingEntity r on r.id = membership.id.readingId
          where collection.key = :key and collection.active = true
            and r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
          """,
      nativeQuery = false)
  Page<ReadingEntity> findReadingsByCollectionKey(@Param("key") String key, Pageable pageable);

  // Language‑scoped query (canonical language tag, no lower())
  @Query(
      value =
          """
          select r
          from ReadingCollectionMembershipEntity membership
          join ReadingCollectionEntity collection on collection.id = membership.id.collectionId
          join ReadingEntity r on r.id = membership.id.readingId
          where collection.key = :key and collection.active = true
            and r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
            and r.language = :language
          order by membership.displayOrder, r.id
          """,
      countQuery =
          """
          select count(membership)
          from ReadingCollectionMembershipEntity membership
          join ReadingCollectionEntity collection on collection.id = membership.id.collectionId
          join ReadingEntity r on r.id = membership.id.readingId
          where collection.key = :key and collection.active = true
            and r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
            and r.language = :language
          """,
      nativeQuery = false)
  Page<ReadingEntity> findReadingsByCollectionKeyAndLanguage(
      @Param("key") String key, @Param("language") String language, Pageable pageable);
}
