package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserVocabularyReviewHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserVocabularyReviewHistoryRepository
    extends JpaRepository<UserVocabularyReviewHistoryEntity, UUID> {

  List<UserVocabularyReviewHistoryEntity> findByUserVocabularyIdOrderByReviewedAtDesc(
      UUID userVocabularyId);

  Page<UserVocabularyReviewHistoryEntity> findByUserIdOrderByReviewedAtDesc(
      UUID userId, Pageable pageable);
}
