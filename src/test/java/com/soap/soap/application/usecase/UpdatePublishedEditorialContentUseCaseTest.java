package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.EditorialOptionCommand;
import com.soap.soap.application.command.EditorialQuestionCommand;
import com.soap.soap.application.command.UpdatePublishedEditorialContentCommand;
import com.soap.soap.application.exception.EditorialContentUpdateException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.ReadingWordFrequencyRepositoryPort;
import com.soap.soap.application.service.EditorialQuizValidator;
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatePublishedEditorialContentUseCaseTest {

  @Mock private ReadingRepositoryPort readings;
  @Mock private ComprehensionQuizRepositoryPort comprehensionQuizzes;
  @Mock private ReadingWordFrequencyRepositoryPort wordFrequencyRepository;
  @Mock private ReadingLexicalIndexer lexicalIndexer;
  @Mock private EditorialQuizValidator quizValidator;

  private UpdatePublishedEditorialContentUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new UpdatePublishedEditorialContentUseCase(
            readings, comprehensionQuizzes, wordFrequencyRepository, lexicalIndexer, quizValidator);
  }

  private Reading createPublishedPlatformReading(String content) {
    return new Reading(
        UUID.randomUUID(),
        null,
        "The MohÃ¡n and the Spring of Water",
        content,
        LanguageTag.of("en"),
        LocalDateTime.of(2026, 1, 1, 12, 0),
        ReadingOrigin.PLATFORM,
        EditorialLevel.B1,
        "Culture, Arts & Fiction",
        "mohan-pasuncha",
        EditorialStatus.PUBLISHED,
        "Short description",
        EditorialContentType.LEGEND,
        "CO",
        EditorialRegion.SOUTH_AMERICA,
        SourceKind.ORAL_TRADITION,
        RightsStatus.ORIGINAL,
        AdaptationKind.PEDAGOGICAL_ADAPTATION,
        LanguageTag.of("es"),
        "Cuento del MohÃ¡n",
        "Comunidad de Pasuncha",
        "https://example.com",
        "Source notes",
        "mohan-pasuncha",
        null,
        AccessTier.FREE);
  }

  @Test
  @DisplayName("Should update content and reindex lexical frequencies when content has changed")
  void shouldUpdateContentAndReindexWhenChanged() {
    var existing = createPublishedPlatformReading("Old content before revision.");
    when(readings.findPlatformReadingByAdaptationKey("mohan-pasuncha", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(existing));
    when(wordFrequencyRepository.findFrequenciesByReadingId(existing.id()))
        .thenReturn(Map.of("new", 1, "approved", 1, "text", 1));

    var command =
        new UpdatePublishedEditorialContentCommand(
            "mohan-pasuncha", "en", EditorialLevel.B1, "New approved editorial text.", null);

    var result = useCase.updateContent(command);

    assertThat(result.readingId()).isEqualTo(existing.id());
    assertThat(result.adaptationGroupKey()).isEqualTo("mohan-pasuncha");
    assertThat(result.contentUpdated()).isTrue();
    assertThat(result.quizUpdated()).isFalse();
    assertThat(result.lexicalFrequencyCount()).isEqualTo(3);

    var captor = ArgumentCaptor.forClass(Reading.class);
    verify(readings).saveAndFlush(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.id()).isEqualTo(existing.id());
    assertThat(saved.content()).isEqualTo("New approved editorial text.");
    assertThat(saved.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);
    assertThat(saved.origin()).isEqualTo(ReadingOrigin.PLATFORM);
    assertThat(saved.adaptationGroupKey()).isEqualTo("mohan-pasuncha");

    verify(lexicalIndexer).indexReading(existing.id(), "en", "New approved editorial text.");
    verify(comprehensionQuizzes, never()).replaceQuestions(any(), any());
  }

  @Test
  @DisplayName(
      "Should be idempotent when content is technically equivalent (CRLF vs LF, trailing whitespace)")
  void shouldBeIdempotentWhenContentTechnicallyEquivalent() {
    var existing = createPublishedPlatformReading("Line 1  \r\nLine 2\r\n");
    when(readings.findPlatformReadingByAdaptationKey("mohan-pasuncha", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(existing));
    when(wordFrequencyRepository.findFrequenciesByReadingId(existing.id()))
        .thenReturn(Map.of("line", 2));

    var command =
        new UpdatePublishedEditorialContentCommand(
            "mohan-pasuncha", "en", EditorialLevel.B1, "Line 1\nLine 2", null);

    var result = useCase.updateContent(command);

    assertThat(result.contentUpdated()).isFalse();
    assertThat(result.quizUpdated()).isFalse();
    assertThat(result.lexicalFrequencyCount()).isEqualTo(1);

    verify(readings, never()).saveAndFlush(any());
    verify(lexicalIndexer, never()).indexReading(any(), any(), any());
    verify(comprehensionQuizzes, never()).replaceQuestions(any(), any());
  }

  @Test
  @DisplayName("Should update comprehension quiz atomically when non-empty questions supplied")
  void shouldUpdateQuizWhenQuestionsSupplied() {
    var existing = createPublishedPlatformReading("Content");
    when(readings.findPlatformReadingByAdaptationKey("mohan-pasuncha", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(existing));
    when(wordFrequencyRepository.findFrequenciesByReadingId(existing.id()))
        .thenReturn(Map.of("content", 1));

    var questions =
        List.of(
            new EditorialQuestionCommand(
                1,
                QuestionType.FACTUAL,
                "What is the prompt?",
                "Here is the explanation.",
                List.of(
                    new EditorialOptionCommand(1, "Option A", true),
                    new EditorialOptionCommand(2, "Option B", false),
                    new EditorialOptionCommand(3, "Option C", false),
                    new EditorialOptionCommand(4, "Option D", false))));

    var command =
        new UpdatePublishedEditorialContentCommand(
            "mohan-pasuncha", "en", EditorialLevel.B1, "Content", questions);

    var result = useCase.updateContent(command);

    assertThat(result.quizUpdated()).isTrue();
    verify(quizValidator).validateCommands(questions);
    verify(comprehensionQuizzes).replaceQuestions(eq(existing.id()), any());
  }

  @Test
  @DisplayName("Should reject update if questions list is empty (must be omitted or non-empty)")
  void shouldRejectEmptyQuestionsList() {
    var command =
        new UpdatePublishedEditorialContentCommand(
            "mohan-pasuncha", "en", EditorialLevel.B1, "Content", List.of());

    assertThatThrownBy(() -> useCase.updateContent(command))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Comprehension quiz questions list cannot be empty when supplied");
  }

  @Test
  @DisplayName("Should reject update if reading is not found")
  void shouldRejectWhenNotFound() {
    when(readings.findPlatformReadingByAdaptationKey("unknown-group", "en", EditorialLevel.B1))
        .thenReturn(Optional.empty());

    var command =
        new UpdatePublishedEditorialContentCommand(
            "unknown-group", "en", EditorialLevel.B1, "Content", null);

    assertThatThrownBy(() -> useCase.updateContent(command))
        .isInstanceOf(EditorialContentUpdateException.class)
        .hasMessageContaining("Reading not found");
  }

  @Test
  @DisplayName("Should reject update if reading is not PLATFORM origin")
  void shouldRejectWhenNotPlatform() {
    var userReading =
        new Reading(
            UUID.randomUUID(),
            new User(UUID.randomUUID(), "User", "user@test.com"),
            "User Title",
            "User content",
            "en",
            LocalDateTime.now());

    when(readings.findPlatformReadingByAdaptationKey("mohan-pasuncha", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(userReading));

    var command =
        new UpdatePublishedEditorialContentCommand(
            "mohan-pasuncha", "en", EditorialLevel.B1, "Content", null);

    assertThatThrownBy(() -> useCase.updateContent(command))
        .isInstanceOf(EditorialContentUpdateException.class)
        .hasMessageContaining("Only PLATFORM readings can be updated");
  }

  @Test
  @DisplayName("Should reject update if reading status is DRAFT")
  void shouldRejectWhenDraft() {
    var draftReading =
        new Reading(
            UUID.randomUUID(),
            null,
            "Title",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Category",
            "mohan-pasuncha",
            EditorialStatus.DRAFT,
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.ORIGINAL,
            AdaptationKind.PEDAGOGICAL_ADAPTATION,
            null,
            null,
            null,
            null,
            null,
            "mohan-pasuncha",
            null,
            AccessTier.FREE);

    when(readings.findPlatformReadingByAdaptationKey("mohan-pasuncha", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(draftReading));

    var command =
        new UpdatePublishedEditorialContentCommand(
            "mohan-pasuncha", "en", EditorialLevel.B1, "New Content", null);

    assertThatThrownBy(() -> useCase.updateContent(command))
        .isInstanceOf(EditorialContentUpdateException.class)
        .hasMessageContaining("Reading is not PUBLISHED");
  }

  @Test
  @DisplayName("Should validate technical normalization logic")
  void testTechnicalNormalization() {
    String t1 = "Hello world  \r\nLine 2   \r\n\r\n";
    String t2 = "Hello world\nLine 2\n";
    String t3 = "Hello world\nLine 2";
    assertThat(UpdatePublishedEditorialContentUseCase.normalizeTechnicalFormatting(t1))
        .isEqualTo("Hello world\nLine 2");
    assertThat(UpdatePublishedEditorialContentUseCase.normalizeTechnicalFormatting(t2))
        .isEqualTo("Hello world\nLine 2");
    assertThat(UpdatePublishedEditorialContentUseCase.normalizeTechnicalFormatting(t3))
        .isEqualTo("Hello world\nLine 2");
  }
}
