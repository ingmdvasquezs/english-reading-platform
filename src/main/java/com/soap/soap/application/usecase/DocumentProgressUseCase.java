package com.soap.soap.application.usecase;

import com.soap.soap.application.command.UpdateDocumentProgressCommand;
import com.soap.soap.application.exception.DocumentProgressConflictException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.DocumentProgressView;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DocumentProgressRepositoryPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.domain.model.DocumentProgress;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DocumentProgressUseCase {
  private final CurrentUserPort currentUser;
  private final ImportedDocumentRepositoryPort documents;
  private final DocumentProgressRepositoryPort progress;
  private final DocumentQueryUseCase queries;
  private final Clock clock;

  @Transactional(readOnly = true)
  public DocumentProgressView get(java.util.UUID documentId) {
    var userId = currentUser.requireUserId();
    queries.owned(documentId, userId);
    return progress
        .findByUserIdAndDocumentId(userId, documentId)
        .map(DocumentProgressView::from)
        .orElseGet(() -> DocumentProgressView.notStarted(documentId));
  }

  @Transactional
  public DocumentProgressView update(UpdateDocumentProgressCommand command) {
    if (command == null || command.documentId() == null || command.currentUnitId() == null) {
      throw new InvalidApplicationArgumentException("Document and current unit are required");
    }
    var userId = currentUser.requireUserId();
    var document = queries.owned(command.documentId(), userId);
    queries.requireReady(document);
    documents
        .findUnitById(document.id(), command.currentUnitId())
        .orElseThrow(
            () ->
                new com.soap.soap.application.exception.DocumentUnitNotFoundException(
                    command.currentUnitId()));
    var now = LocalDateTime.now(clock);
    var existing = progress.findByUserIdAndDocumentId(userId, document.id()).orElse(null);
    if (existing != null
        && command.expectedVersion() != null
        && !command.expectedVersion().equals(existing.version())) {
      throw new DocumentProgressConflictException();
    }
    DocumentProgress updated =
        existing == null
            ? DocumentProgress.start(userId, document.id(), command.currentUnitId(), now)
            : existing.moveTo(command.currentUnitId(), now);
    if (command.completed()) updated = updated.complete(command.currentUnitId(), now);
    return DocumentProgressView.from(progress.save(updated));
  }
}
