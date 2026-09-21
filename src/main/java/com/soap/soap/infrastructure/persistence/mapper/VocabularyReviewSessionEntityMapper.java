package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.VocabularyReviewSession;
import com.soap.soap.domain.model.VocabularyReviewSessionItem;
import com.soap.soap.infrastructure.persistence.entity.UserEntity;
import com.soap.soap.infrastructure.persistence.entity.VocabularyReviewSessionEntity;
import com.soap.soap.infrastructure.persistence.entity.VocabularyReviewSessionItemEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VocabularyReviewSessionEntityMapper {
  private final UserVocabularyEntityMapper userVocabularyMapper;

  public VocabularyReviewSession toDomain(VocabularyReviewSessionEntity entity) {
    if (entity == null) {
      return null;
    }
    List<VocabularyReviewSessionItem> domainItems =
        entity.getItems() == null
            ? List.of()
            : entity.getItems().stream().map(this::toDomainItem).toList();

    return new VocabularyReviewSession(
        entity.getId(),
        entity.getUser().getId(),
        entity.getLocalReviewDate(),
        entity.getStatus(),
        entity.getDailyLimit(),
        entity.getNextQueueSequence(),
        entity.getCreatedAt(),
        entity.getCompletedAt(),
        domainItems);
  }

  public VocabularyReviewSessionItem toDomainItem(VocabularyReviewSessionItemEntity entity) {
    if (entity == null) {
      return null;
    }
    return new VocabularyReviewSessionItem(
        entity.getId(),
        entity.getSession() != null ? entity.getSession().getId() : null,
        userVocabularyMapper.toDomain(entity.getUserVocabulary()),
        entity.getBaseOrder(),
        entity.getIntroducedAt(),
        entity.getPendingQueueSequence());
  }

  public VocabularyReviewSessionEntity toEntity(
      VocabularyReviewSession domain, UserEntity userEntity) {
    if (domain == null) {
      return null;
    }
    var sessionEntity =
        VocabularyReviewSessionEntity.builder()
            .id(domain.id() != null ? domain.id() : UUID.randomUUID())
            .user(userEntity)
            .localReviewDate(domain.localReviewDate())
            .status(domain.status())
            .dailyLimit(domain.dailyLimit())
            .nextQueueSequence(domain.nextQueueSequence())
            .createdAt(domain.createdAt())
            .completedAt(domain.completedAt())
            .items(new ArrayList<>())
            .build();

    if (domain.items() != null) {
      for (var itemDomain : domain.items()) {
        var itemEntity =
            VocabularyReviewSessionItemEntity.builder()
                .id(itemDomain.id() != null ? itemDomain.id() : UUID.randomUUID())
                .session(sessionEntity)
                .userVocabulary(userVocabularyMapper.toEntity(itemDomain.vocabulary()))
                .baseOrder(itemDomain.baseOrder())
                .introducedAt(itemDomain.introducedAt())
                .pendingQueueSequence(itemDomain.pendingQueueSequence())
                .build();
        sessionEntity.addItem(itemEntity);
      }
    }
    return sessionEntity;
  }
}
