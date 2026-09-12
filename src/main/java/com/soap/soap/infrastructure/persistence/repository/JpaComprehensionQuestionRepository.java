package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ComprehensionQuestionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaComprehensionQuestionRepository
    extends JpaRepository<ComprehensionQuestionEntity, UUID> {
  List<ComprehensionQuestionEntity> findByReadingIdOrderByOrdinalAsc(UUID readingId);
}
