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
          """)
  Page<ReadingEntity> findReadingsByCollectionKey(@Param("key") String key, Pageable pageable);
}
