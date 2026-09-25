package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.DocumentUploadRepositoryPort;
import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.domain.model.DocumentUploadStatus;
import com.soap.soap.infrastructure.persistence.mapper.DocumentUploadEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaDocumentUploadRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DocumentUploadPersistenceAdapter implements DocumentUploadRepositoryPort {

  private final JpaDocumentUploadRepository repository;
  private final DocumentUploadEntityMapper mapper;
  private final Clock clock;

  @Override
  @Transactional
  public DocumentUpload save(DocumentUpload upload) {
    var entity = mapper.toEntity(upload);
    return mapper.toDomain(repository.saveAndFlush(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentUpload> findById(UUID uploadId) {
    return repository.findById(uploadId).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentUpload> findByDocumentId(UUID documentId) {
    return repository.findByDocumentId(documentId).map(mapper::toDomain);
  }

  @Override
  @Transactional
  public Optional<DocumentUpload> findByIdAndLock(UUID uploadId) {
    return repository.findByIdForUpdate(uploadId).map(mapper::toDomain);
  }

  @Override
  @Transactional
  public boolean abort(UUID uploadId, UUID userId) {
    var entityOpt = repository.findByIdForUpdate(uploadId);
    if (entityOpt.isEmpty()) {
      return false;
    }
    var entity = entityOpt.get();
    if (!entity.getUserId().equals(userId)) {
      return false;
    }
    if (entity.getStatus() == DocumentUploadStatus.CONFIRMED) {
      return false;
    }
    entity.setStatus(DocumentUploadStatus.ABORTED);
    entity.setUpdatedAt(LocalDateTime.now(clock));
    repository.saveAndFlush(entity);
    return true;
  }
}
