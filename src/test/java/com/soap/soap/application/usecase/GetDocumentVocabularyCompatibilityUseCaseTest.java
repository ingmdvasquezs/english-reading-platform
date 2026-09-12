package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.DocumentNotFoundException;
import com.soap.soap.application.exception.DocumentNotReadyException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ImportedDocumentRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.VocabularyBreakdownCalculator;
import com.soap.soap.application.service.VocabularyCompatibilityCalculator;
import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.domain.model.DocumentUnitKind;
import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.domain.model.VocabularyStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetDocumentVocabularyCompatibilityUseCaseTest {
  @Mock private CurrentUserPort currentUser;
  @Mock private ImportedDocumentRepositoryPort documents;
  @Mock private UserVocabularyRepositoryPort vocabulary;

  private final TextWordProcessor textWordProcessor = new TextWordProcessor();
  private final VocabularyCompatibilityCalculator compatibilityCalculator =
      new VocabularyCompatibilityCalculator(new VocabularyBreakdownCalculator());

  private GetDocumentVocabularyCompatibilityUseCase useCase;

  private final UUID userId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase =
        new GetDocumentVocabularyCompatibilityUseCase(
            currentUser, documents, vocabulary, textWordProcessor, compatibilityCalculator);
    when(currentUser.requireUserId()).thenReturn(userId);
  }

  @Test
  void multiUnitWithRepeatedWordsDeduplicatesCorrectly() {
    var document = readyDocument(documentId, userId, "en", DocumentFormat.EPUB);
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));

    var unit1 = unit(documentId, 1, "harbor morning quiet");
    var unit2 = unit(documentId, 2, "harbor market morning");
    when(documents.findUnits(documentId)).thenReturn(List.of(unit1, unit2));

    // Unique words: harbor, morning, quiet, market (4 words)
    when(vocabulary.findStatusesByUserAndLanguage(userId, "en"))
        .thenReturn(
            Map.of(
                "harbor", VocabularyStatus.KNOWN,
                "morning", VocabularyStatus.LEARNING,
                "market", VocabularyStatus.NEW));
    // "quiet" is absent -> UNCLASSIFIED

    var result = useCase.getCompatibility(documentId);

    assertThat(result.documentId()).isEqualTo(documentId);
    assertThat(result.uniqueWords()).isEqualTo(4);
    assertThat(result.knownWords()).isEqualTo(1);
    assertThat(result.learningWords()).isEqualTo(1);
    assertThat(result.explicitNewWords()).isEqualTo(1);
    assertThat(result.ignoredWords()).isEqualTo(0);
    assertThat(result.unclassifiedWords()).isEqualTo(1);
    // fit = 100 - (0.50*1 + 1.00*1 + 0.70*1)*100/4 = 100 - 2.20*100/4 = 100 - 55.00 = 45.00
    assertThat(result.vocabularyFitPercentage()).isEqualTo(new BigDecimal("45.00"));
    // confidence = (4 - 1)*100/4 = 75.00
    assertThat(result.classificationConfidencePercentage()).isEqualTo(new BigDecimal("75.00"));
  }

  @Test
  void unclassifiedVsNewDistinctionIsPreserved() {
    var document = readyDocument(documentId, userId, "en", DocumentFormat.EPUB);
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));

    var unit = unit(documentId, 1, "whisper cavern");
    when(documents.findUnits(documentId)).thenReturn(List.of(unit));

    // Case 1: whisper is NEW, cavern is unclassified (absent)
    when(vocabulary.findStatusesByUserAndLanguage(userId, "en"))
        .thenReturn(Map.of("whisper", VocabularyStatus.NEW));

    var result = useCase.getCompatibility(documentId);

    assertThat(result.uniqueWords()).isEqualTo(2);
    assertThat(result.explicitNewWords()).isEqualTo(1);
    assertThat(result.unclassifiedWords()).isEqualTo(1);
    // NEW has weight 1.00, UNCLASSIFIED has weight 0.70
    // fit = 100 - (1.00*1 + 0.70*1)*100/2 = 100 - 85.00 = 15.00
    assertThat(result.vocabularyFitPercentage()).isEqualTo(new BigDecimal("15.00"));
    // confidence = (2 - 1)*100/2 = 50.00 (NEW counts towards confidence, UNCLASSIFIED does not)
    assertThat(result.classificationConfidencePercentage()).isEqualTo(new BigDecimal("50.00"));
  }

  @Test
  void ignoredWordsDoNotCountAsChallenge() {
    var document = readyDocument(documentId, userId, "en", DocumentFormat.EPUB);
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));

    var unit = unit(documentId, 1, "river river river stream");
    when(documents.findUnits(documentId)).thenReturn(List.of(unit));

    when(vocabulary.findStatusesByUserAndLanguage(userId, "en"))
        .thenReturn(
            Map.of(
                "river", VocabularyStatus.IGNORED,
                "stream", VocabularyStatus.KNOWN));

    var result = useCase.getCompatibility(documentId);

    assertThat(result.uniqueWords()).isEqualTo(2);
    assertThat(result.ignoredWords()).isEqualTo(1);
    assertThat(result.knownWords()).isEqualTo(1);
    assertThat(result.learningWords()).isEqualTo(0);
    assertThat(result.explicitNewWords()).isEqualTo(0);
    assertThat(result.unclassifiedWords()).isEqualTo(0);
    // IGNORED has weight 0 in challenge, KNOWN has weight 0
    assertThat(result.vocabularyFitPercentage()).isEqualTo(new BigDecimal("100.00"));
    assertThat(result.classificationConfidencePercentage()).isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void dynamicUpdateWithoutDocumentModificationReflectsInNextCall() {
    var document = readyDocument(documentId, userId, "en", DocumentFormat.EPUB);
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));

    var unit = unit(documentId, 1, "harbor sunrise");
    when(documents.findUnits(documentId)).thenReturn(List.of(unit));

    // Call 1: harbor is absent -> UNCLASSIFIED
    when(vocabulary.findStatusesByUserAndLanguage(userId, "en"))
        .thenReturn(Map.of("sunrise", VocabularyStatus.KNOWN));

    var result1 = useCase.getCompatibility(documentId);
    assertThat(result1.knownWords()).isEqualTo(1);
    assertThat(result1.unclassifiedWords()).isEqualTo(1);
    assertThat(result1.vocabularyFitPercentage()).isEqualTo(new BigDecimal("65.00"));
    assertThat(result1.classificationConfidencePercentage()).isEqualTo(new BigDecimal("50.00"));

    // Call 2: user marks harbor as KNOWN
    when(vocabulary.findStatusesByUserAndLanguage(userId, "en"))
        .thenReturn(
            Map.of(
                "sunrise", VocabularyStatus.KNOWN,
                "harbor", VocabularyStatus.KNOWN));

    var result2 = useCase.getCompatibility(documentId);
    assertThat(result2.knownWords()).isEqualTo(2);
    assertThat(result2.unclassifiedWords()).isEqualTo(0);
    assertThat(result2.vocabularyFitPercentage()).isEqualTo(new BigDecimal("100.00"));
    assertThat(result2.classificationConfidencePercentage()).isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void emptyLexicalDocumentReturnsCalculatorDefaults() {
    var document = readyDocument(documentId, userId, "en", DocumentFormat.EPUB);
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));
    when(documents.findUnits(documentId)).thenReturn(List.of());
    when(vocabulary.findStatusesByUserAndLanguage(userId, "en")).thenReturn(Map.of());

    var result = useCase.getCompatibility(documentId);

    assertThat(result.uniqueWords()).isEqualTo(0);
    assertThat(result.knownWords()).isEqualTo(0);
    assertThat(result.learningWords()).isEqualTo(0);
    assertThat(result.explicitNewWords()).isEqualTo(0);
    assertThat(result.ignoredWords()).isEqualTo(0);
    assertThat(result.unclassifiedWords()).isEqualTo(0);
    assertThat(result.vocabularyFitPercentage()).isEqualTo(new BigDecimal("100.00"));
    assertThat(result.classificationConfidencePercentage()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void ownershipFailureThrowsDocumentNotFoundException() {
    when(documents.findDocumentByIdAndOwnerId(documentId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.getCompatibility(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void documentProcessingStatusThrowsDocumentNotReadyException() {
    var document =
        new ImportedDocument(
            documentId,
            userId,
            "Title",
            "Author",
            "en",
            DocumentFormat.EPUB,
            null,
            "source",
            "test.epub",
            "a".repeat(64),
            DocumentImportStatus.PROCESSING,
            1,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));

    assertThatThrownBy(() -> useCase.getCompatibility(documentId))
        .isInstanceOf(DocumentNotReadyException.class);
  }

  @Test
  void documentFailedStatusThrowsDocumentNotReadyException() {
    var document =
        new ImportedDocument(
            documentId,
            userId,
            "Title",
            "Author",
            "en",
            DocumentFormat.EPUB,
            null,
            "source",
            "test.epub",
            "a".repeat(64),
            DocumentImportStatus.FAILED,
            "INVALID_EPUB",
            1,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));

    assertThatThrownBy(() -> useCase.getCompatibility(documentId))
        .isInstanceOf(DocumentNotReadyException.class);
  }

  @Test
  void epubAndPdfProduceIdenticalMetricsForSameContent() {
    var epubDoc = readyDocument(documentId, userId, "en", DocumentFormat.EPUB);
    var pdfDoc = readyDocument(documentId, userId, "en", DocumentFormat.PDF);

    var units = List.of(unit(documentId, 1, "ancient castle stones stood still"));
    var statuses =
        Map.of(
            "ancient", VocabularyStatus.KNOWN,
            "castle", VocabularyStatus.LEARNING,
            "stones", VocabularyStatus.NEW);

    when(documents.findUnits(documentId)).thenReturn(units);
    when(vocabulary.findStatusesByUserAndLanguage(userId, "en")).thenReturn(statuses);

    when(documents.findDocumentByIdAndOwnerId(documentId, userId)).thenReturn(Optional.of(epubDoc));
    var epubResult = useCase.getCompatibility(documentId);

    when(documents.findDocumentByIdAndOwnerId(documentId, userId)).thenReturn(Optional.of(pdfDoc));
    var pdfResult = useCase.getCompatibility(documentId);

    assertThat(epubResult.uniqueWords()).isEqualTo(pdfResult.uniqueWords());
    assertThat(epubResult.knownWords()).isEqualTo(pdfResult.knownWords());
    assertThat(epubResult.learningWords()).isEqualTo(pdfResult.learningWords());
    assertThat(epubResult.explicitNewWords()).isEqualTo(pdfResult.explicitNewWords());
    assertThat(epubResult.unclassifiedWords()).isEqualTo(pdfResult.unclassifiedWords());
    assertThat(epubResult.vocabularyFitPercentage()).isEqualTo(pdfResult.vocabularyFitPercentage());
    assertThat(epubResult.classificationConfidencePercentage())
        .isEqualTo(pdfResult.classificationConfidencePercentage());
  }

  @Test
  void largeVolumeMultiUnitProcessesCorrectlyWithoutExplosion() {
    var document = readyDocument(documentId, userId, "en", DocumentFormat.EPUB);
    when(documents.findDocumentByIdAndOwnerId(documentId, userId))
        .thenReturn(Optional.of(document));

    // Create 100 units with repeated vocabulary
    var sampleWords =
        List.of(
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa",
            "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi",
            "chi", "psi", "omega");
    var units = new ArrayList<DocumentUnit>();
    for (int i = 1; i <= 100; i++) {
      units.add(
          unit(
              documentId,
              i,
              sampleWords.get(i % sampleWords.size()) + " common book narrative chapter page"));
    }
    when(documents.findUnits(documentId)).thenReturn(units);
    when(vocabulary.findStatusesByUserAndLanguage(userId, "en"))
        .thenReturn(
            Map.of(
                "common", VocabularyStatus.KNOWN,
                "book", VocabularyStatus.KNOWN,
                "chapter", VocabularyStatus.LEARNING));

    var result = useCase.getCompatibility(documentId);

    assertThat(result.uniqueWords()).isGreaterThan(20);
    assertThat(result.knownWords()).isEqualTo(2);
    assertThat(result.learningWords()).isEqualTo(1);
    assertThat(result.vocabularyFitPercentage()).isNotNull();
    assertThat(result.classificationConfidencePercentage()).isNotNull();
  }

  private ImportedDocument readyDocument(
      UUID id, UUID ownerId, String language, DocumentFormat format) {
    var now = LocalDateTime.now();
    return new ImportedDocument(
        id,
        ownerId,
        "Book Title",
        "Author",
        language,
        format,
        "cover-key",
        "source-key",
        "file." + format.name().toLowerCase(),
        "a".repeat(64),
        DocumentImportStatus.READY,
        1,
        now,
        now);
  }

  private DocumentUnit unit(UUID docId, int globalOrdinal, String content) {
    return new DocumentUnit(
        UUID.randomUUID(),
        docId,
        UUID.randomUUID(),
        globalOrdinal,
        1,
        DocumentUnitKind.LOGICAL_CHUNK,
        content,
        content.split("\\s+").length,
        "locator",
        null);
  }
}
