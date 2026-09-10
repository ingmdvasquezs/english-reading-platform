package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.domain.model.DocumentProgress;
import com.soap.soap.infrastructure.persistence.mapper.DocumentProgressEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaDocumentProgressRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DocumentProgressPersistenceAdapter implements DocumentProgressRepositoryPort {
  private final JpaDocumentProgressRepository repository;
  private final DocumentProgressEntityMapper mapper;

  @Override
  @Transactional
  public DocumentProgress save(DocumentProgress progress) {
    return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(progress)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentProgress> findByUserIdAndDocumentId(UUID userId, UUID documentId) {
    return repository.findByUserIdAndDocumentId(userId, documentId).map(mapper::toDomain);
  }
}
