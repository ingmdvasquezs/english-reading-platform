package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.VocabularyReviewSessionRepositoryPort;
import com.soap.soap.domain.model.VocabularyReviewSession;
import com.soap.soap.infrastructure.persistence.mapper.VocabularyReviewSessionEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaUserRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaVocabularyReviewSessionRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyReviewSessionPersistenceAdapter
    implements VocabularyReviewSessionRepositoryPort {

  private final JpaVocabularyReviewSessionRepository sessionRepository;
  private final JpaUserRepository userRepository;
  private final VocabularyReviewSessionEntityMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Optional<VocabularyReviewSession> findSession(UUID userId, LocalDate localDate) {
    return sessionRepository
        .findByUserIdAndLocalReviewDate(userId, localDate)
        .map(mapper::toDomain);
  }

  @Override
  @Transactional
  public Optional<VocabularyReviewSession> findSessionForUpdate(UUID userId, LocalDate localDate) {
    sessionRepository.findByUserIdAndLocalReviewDateForUpdate(userId, localDate);
    return sessionRepository
        .findByUserIdAndLocalReviewDate(userId, localDate)
        .map(mapper::toDomain);
  }

  @Override
  @Transactional
  public void acquireSessionCreationLock(UUID userId) {
    userRepository.findByIdForUpdate(userId);
  }

  @Override
  @Transactional
  public VocabularyReviewSession saveSession(VocabularyReviewSession session) {
    try {
      var existingOpt =
          sessionRepository.findByUserIdAndLocalReviewDate(
              session.userId(), session.localReviewDate());
      if (existingOpt.isPresent()) {
        var existing = existingOpt.get();
        existing.setStatus(session.status());
        existing.setCompletedAt(session.completedAt());
        existing.setNextQueueSequence(session.nextQueueSequence());

        for (var itemDomain : session.items()) {
          for (var itemEntity : existing.getItems()) {
            if (itemEntity.getUserVocabulary().getId().equals(itemDomain.vocabulary().id())) {
              itemEntity.setIntroducedAt(itemDomain.introducedAt());
              itemEntity.setPendingQueueSequence(itemDomain.pendingQueueSequence());
              break;
            }
          }
        }
        var saved = sessionRepository.saveAndFlush(existing);
        return mapper.toDomain(saved);
      } else {
        var userEntity =
            userRepository
                .findById(session.userId())
                .orElseThrow(
                    () -> new IllegalStateException("User not found: " + session.userId()));
        var entity = mapper.toEntity(session, userEntity);
        var saved = sessionRepository.saveAndFlush(entity);
        return mapper.toDomain(saved);
      }
    } catch (DataIntegrityViolationException dive) {
      log.warn(
          "Concurrent review session creation detected for user {} on date {}. Recovering existing"
              + " session.",
          session.userId(),
          session.localReviewDate());
      return sessionRepository
          .findByUserIdAndLocalReviewDate(session.userId(), session.localReviewDate())
          .map(mapper::toDomain)
          .orElseThrow(() -> dive);
    }
  }
}
