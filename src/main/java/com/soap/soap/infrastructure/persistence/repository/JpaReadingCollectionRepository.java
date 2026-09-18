package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaReadingCollectionRepository
    extends JpaRepository<ReadingCollectionEntity, UUID> {
  List<ReadingCollectionEntity> findByActiveTrueOrderByDisplayOrderAscIdAsc();

  Optional<ReadingCollectionEntity> findByKeyAndActiveTrue(String key);

  Optional<ReadingCollectionEntity> findByKey(String key);

  @Query(
      """
      select c from ReadingCollectionEntity c
      where c.active = true
        and exists (
          select 1 from ReadingCollectionMembershipEntity m
          join ReadingEntity r on r.id = m.id.readingId
          where m.id.collectionId = c.id
            and r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
        )
      order by c.displayOrder asc, c.id asc
      """)
  List<ReadingCollectionEntity> findActiveWithPublishedReadings();

  @Query(
      """
      select c, count(r.id)
      from ReadingCollectionEntity c
      left join ReadingCollectionMembershipEntity m on m.id.collectionId = c.id
      left join ReadingEntity r on r.id = m.id.readingId
        and r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
        and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
      where c.active = true
      group by c.id, c.key, c.displayName, c.description, c.displayOrder, c.active, c.coverKey
      having count(r.id) > 0
      order by c.displayOrder asc, c.id asc
      """)
  List<Object[]> findActiveWithPublishedReadingCounts();
}
