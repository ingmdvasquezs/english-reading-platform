package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.DocumentNotReadyException;
import com.soap.soap.application.model.DocumentVocabularyCompatibilityView;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.VocabularyCompatibilityCalculator;
import com.soap.soap.domain.model.DocumentImportStatus;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetDocumentVocabularyCompatibilityUseCase {
  private final CurrentUserPort currentUser;
  private final ImportedDocumentRepositoryPort documents;
  private final UserVocabularyRepositoryPort vocabulary;
  private final TextWordProcessor textWordProcessor;
  private final VocabularyCompatibilityCalculator compatibilityCalculator;

  @Transactional(readOnly = true)
  public DocumentVocabularyCompatibilityView getCompatibility(UUID documentId) {
    var userId = currentUser.requireUserId();
    var document =
        documents
            .findDocumentByIdAndOwnerId(documentId, userId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

    if (document.importStatus() != DocumentImportStatus.READY) {
      throw new DocumentNotReadyException();
    }

    var units = documents.findUnits(documentId);
    Set<String> uniqueWords = new LinkedHashSet<>();
    for (var unit : units) {
      if (unit.content() != null && !unit.content().isBlank()) {
        for (var token : textWordProcessor.tokenize(unit.content())) {
          uniqueWords.add(token.normalizedValue());
        }
      }
    }

    var statuses = vocabulary.findStatusesByUserAndLanguage(userId, document.language());
    var compatibility = compatibilityCalculator.calculate(uniqueWords, statuses);
    var breakdown = compatibility.breakdown();

    return new DocumentVocabularyCompatibilityView(
        documentId,
        breakdown.uniqueWords(),
        breakdown.knownWords(),
        breakdown.learningWords(),
        breakdown.explicitNewWords(),
        breakdown.ignoredWords(),
        breakdown.unclassifiedWords(),
        compatibility.vocabularyFitPercentage(),
        compatibility.classificationConfidencePercentage());
  }
}
