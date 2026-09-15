package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReadingRepository extends JpaRepository<ReadingEntity, UUID> {

  @Override
  @EntityGraph(attributePaths = "user")
  Optional<ReadingEntity> findById(UUID id);

  Optional<ReadingEntity> findByAdaptationGroupKeyAndLanguageAndEditorialLevelAndOrigin(
      String adaptationGroupKey,
      String language,
      com.soap.soap.domain.model.EditorialLevel editorialLevel,
      com.soap.soap.domain.model.ReadingOrigin origin);

  @Query(
      value =
          """
          select r.id as id, r.title as title, r.language as language, r.createdAt as createdAt
          from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.USER
            and r.user.id = :userId
          order by r.createdAt desc
          """,
      countQuery =
          """
          select count(r) from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.USER
            and r.user.id = :userId
          """)
  Page<ReadingSummaryView> findSummariesByUserId(@Param("userId") UUID userId, Pageable pageable);

  @EntityGraph(attributePaths = "user")
  @Query(
      value =
          """
          select r from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.USER
            and r.user.id = :userId
          order by r.createdAt desc, r.id
          """,
      countQuery =
          """
          select count(r) from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.USER
            and r.user.id = :userId
          """)
  Page<ReadingEntity> findUserReadingsByUserId(@Param("userId") UUID userId, Pageable pageable);

  @Query(
      value =
          """
          select r.id as id, r.title as title, r.language as language,
                 r.editorialLevel as editorialLevel, r.category as category,
                 r.createdAt as createdAt, r.coverKey as coverKey,
                 r.shortDescription as shortDescription, r.contentType as contentType,
                 r.countryCode as countryCode, r.region as region,
                 r.accessTier as accessTier
          from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
          order by r.createdAt desc, r.id
          """,
      countQuery =
          """
          select count(r) from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
          """)
  Page<PlatformReadingSummaryView> findPlatformSummaries(Pageable pageable);

  @Query(
      value =
          """
          select r.id as id, r.title as title, r.language as language,
                 r.editorialLevel as editorialLevel, r.category as category,
                 r.createdAt as createdAt, r.coverKey as coverKey,
                 r.shortDescription as shortDescription, r.contentType as contentType,
                 r.countryCode as countryCode, r.region as region,
                 r.accessTier as accessTier
          from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
            and lower(r.language) = lower(:language)
          order by r.createdAt desc, r.id
          """,
      countQuery =
          """
          select count(r) from ReadingEntity r
          where r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
            and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
            and lower(r.language) = lower(:language)
          """)
  Page<PlatformReadingSummaryView> findPlatformSummariesByLanguage(
      @Param("language") String language, Pageable pageable);

  @Query(
      """
      select r.id as id, r.title as title, r.language as language,
             r.editorialLevel as editorialLevel, r.category as category,
             r.createdAt as createdAt, r.coverKey as coverKey,
             r.shortDescription as shortDescription, r.contentType as contentType,
             r.countryCode as countryCode, r.region as region,
             r.accessTier as accessTier
      from ReadingEntity r
      where r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
        and r.editorialStatus = com.soap.soap.domain.model.EditorialStatus.PUBLISHED
      order by r.createdAt desc, r.id
      """)
  List<PlatformReadingSummaryView> findAllPlatformSummaries();

  @Query(
      """
      select r from ReadingEntity r
      where r.origin = com.soap.soap.domain.model.ReadingOrigin.PLATFORM
      order by r.createdAt desc, r.id
      """)
  List<ReadingEntity> findAllPlatformReadings();

  interface ReadingSummaryView {
    UUID getId();

    String getTitle();

    String getLanguage();

    LocalDateTime getCreatedAt();
  }

  interface PlatformReadingSummaryView extends ReadingSummaryView {
    com.soap.soap.domain.model.EditorialLevel getEditorialLevel();

    String getCategory();

    String getCoverKey();

    String getShortDescription();

    com.soap.soap.domain.model.EditorialContentType getContentType();

    String getCountryCode();

    com.soap.soap.domain.model.EditorialRegion getRegion();

    com.soap.soap.domain.model.AccessTier getAccessTier();
  }
}
