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
import com.soap.soap.application.command.IngestEditorialReadingCommand;
import com.soap.soap.application.exception.EditorialIngestionException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.policy.LanguageAvailabilityPolicy;
import com.soap.soap.application.port.out.ComprehensionQuizRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestEditorialReadingUseCaseTest {

  @Mock private ReadingRepositoryPort readings;
  @Mock private ComprehensionQuizRepositoryPort comprehensionQuizzes;
  @Mock private ReadingLexicalIndexer lexicalIndexer;

  private LanguageAvailabilityPolicy availabilityPolicy;
  private EditorialQuizValidator quizValidator;
  private IngestEditorialReadingUseCase useCase;

  @BeforeEach
  void setUp() {
    availabilityPolicy = new LanguageAvailabilityPolicy(Set.of("en"), Set.of("en"));
    quizValidator = new EditorialQuizValidator();
    useCase =
        new IngestEditorialReadingUseCase(
            readings, comprehensionQuizzes, lexicalIndexer, availabilityPolicy, quizValidator);
  }

  private IngestEditorialReadingCommand validCommand() {
    var questions = new ArrayList<EditorialQuestionCommand>();
    QuestionType[] types = {
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA,
      QuestionType.FACTUAL,
      QuestionType.INFERENCE,
      QuestionType.MAIN_IDEA
    };
    for (int i = 1; i <= 6; i++) {
      var options =
          List.of(
              new EditorialOptionCommand(1, "Option A", true),
              new EditorialOptionCommand(2, "Option B", false),
              new EditorialOptionCommand(3, "Option C", false),
              new EditorialOptionCommand(4, "Option D", false));
      questions.add(
          new EditorialQuestionCommand(i, types[i - 1], "Prompt " + i, "Exp " + i, options));
    }

    return new IngestEditorialReadingCommand(
        "Fictional Test Tale",
        "A short story for testing editorial ingestion.",
        "en",
        EditorialLevel.B1,
        "Culture, Arts & Fiction",
        "A short description for testing.",
        EditorialContentType.LEGEND,
        "CO",
        EditorialRegion.SOUTH_AMERICA,
        SourceKind.ORAL_TRADITION,
        RightsStatus.PUBLIC_DOMAIN,
        AdaptationKind.PEDAGOGICAL_ADAPTATION,
        "es",
        "Source Story Title",
        "Traditional",
        null,
        "Notes on test adaptation",
        "test-group-key-01",
        "test-cover-slug",
        "Test Team",
        AccessTier.FREE,
        questions);
  }

  @Test
  void validIngestionCreatesDraftPlatformReading() {
    var command = validCommand();
    UUID generatedId = UUID.randomUUID();

    when(readings.findPlatformReadingByAdaptationKey("test-group-key-01", "en", EditorialLevel.B1))
        .thenReturn(Optional.empty());

    when(readings.saveAndFlush(any(Reading.class)))
        .thenAnswer(
            inv -> {
              Reading r = inv.getArgument(0);
              return new Reading(
                  generatedId,
                  r.user(),
                  r.title(),
                  r.content(),
                  r.language(),
                  LocalDateTime.now(),
                  r.origin(),
                  r.editorialLevel(),
                  r.category(),
                  r.coverKey(),
                  r.editorialStatus(),
                  r.shortDescription(),
                  r.contentType(),
                  r.countryCode(),
                  r.region(),
                  r.sourceKind(),
                  r.rightsStatus(),
                  r.adaptationKind(),
                  r.sourceLanguage(),
                  r.sourceTitle(),
                  r.sourceAuthor(),
                  r.sourceUrl(),
                  r.sourceNotes(),
                  r.adaptationGroupKey(),
                  r.coverAttribution(),
                  r.accessTier());
            });

    var result = useCase.ingest(command);

    assertThat(result.created()).isTrue();
    assertThat(result.readingId()).isEqualTo(generatedId);
    assertThat(result.status()).isEqualTo(EditorialStatus.DRAFT);
    assertThat(result.questionsCount()).isEqualTo(6);

    ArgumentCaptor<Reading> captor = ArgumentCaptor.forClass(Reading.class);
    verify(readings).saveAndFlush(captor.capture());
    var saved = captor.getValue();

    assertThat(saved.origin()).isEqualTo(ReadingOrigin.PLATFORM);
    assertThat(saved.user()).isNull();
    assertThat(saved.editorialStatus()).isEqualTo(EditorialStatus.DRAFT);
    assertThat(saved.title()).isEqualTo("Fictional Test Tale");
    assertThat(saved.language().value()).isEqualTo("en");
    assertThat(saved.adaptationGroupKey()).isEqualTo("test-group-key-01");

    verify(comprehensionQuizzes).replaceQuestions(eq(generatedId), any());
    verify(lexicalIndexer).indexReading(eq(generatedId), eq("en"), eq(command.content()));
  }

  @Test
  void reingestionOfSameDraftUpdatesInPlace() {
    var command = validCommand();
    UUID existingId = UUID.randomUUID();
    LocalDateTime initialCreated = LocalDateTime.of(2026, 1, 1, 10, 0);

    var existingDraft =
        new Reading(
            existingId,
            null,
            "Old Title",
            "Old Content",
            LanguageTag.of("en"),
            initialCreated,
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "old-cover",
            EditorialStatus.DRAFT,
            "Old short description",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.PEDAGOGICAL_ADAPTATION,
            LanguageTag.of("es"),
            null,
            null,
            null,
            null,
            "test-group-key-01",
            null,
            AccessTier.FREE);

    when(readings.findPlatformReadingByAdaptationKey("test-group-key-01", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(existingDraft));

    when(readings.saveAndFlush(any(Reading.class))).thenAnswer(inv -> inv.getArgument(0));

    var result = useCase.ingest(command);

    assertThat(result.created()).isFalse();
    assertThat(result.readingId()).isEqualTo(existingId);
    assertThat(result.status()).isEqualTo(EditorialStatus.DRAFT);

    ArgumentCaptor<Reading> captor = ArgumentCaptor.forClass(Reading.class);
    verify(readings).saveAndFlush(captor.capture());
    var updated = captor.getValue();

    assertThat(updated.id()).isEqualTo(existingId);
    assertThat(updated.createdAt()).isEqualTo(initialCreated);
    assertThat(updated.title()).isEqualTo("Fictional Test Tale");
    assertThat(updated.editorialStatus()).isEqualTo(EditorialStatus.DRAFT);

    verify(comprehensionQuizzes).replaceQuestions(eq(existingId), any());
    verify(lexicalIndexer).indexReading(eq(existingId), eq("en"), eq(command.content()));
  }

  @Test
  void rejectsReingestionWhenAlreadyPublished() {
    var command = validCommand();
    UUID existingId = UUID.randomUUID();

    var existingPublished =
        new Reading(
            existingId,
            null,
            "Published Title",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "cover-slug",
            EditorialStatus.PUBLISHED,
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.PEDAGOGICAL_ADAPTATION,
            LanguageTag.of("es"),
            null,
            null,
            null,
            null,
            "test-group-key-01",
            null,
            AccessTier.FREE);

    when(readings.findPlatformReadingByAdaptationKey("test-group-key-01", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(existingPublished));

    assertThatThrownBy(() -> useCase.ingest(command))
        .isInstanceOf(EditorialIngestionException.class)
        .hasMessageContaining("already PUBLISHED");

    verify(readings, never()).save(any());
  }

  @Test
  void rejectsReingestionWhenArchived() {
    var command = validCommand();
    UUID existingId = UUID.randomUUID();

    var existingArchived =
        new Reading(
            existingId,
            null,
            "Archived Title",
            "Content",
            LanguageTag.of("en"),
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "cover-slug",
            EditorialStatus.ARCHIVED,
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.PEDAGOGICAL_ADAPTATION,
            LanguageTag.of("es"),
            null,
            null,
            null,
            null,
            "test-group-key-01",
            null,
            AccessTier.FREE);

    when(readings.findPlatformReadingByAdaptationKey("test-group-key-01", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(existingArchived));

    assertThatThrownBy(() -> useCase.ingest(command))
        .isInstanceOf(EditorialIngestionException.class)
        .hasMessageContaining("ARCHIVED");

    verify(readings, never()).save(any());
  }

  @Test
  void rejectsUnsupportedContentLanguage() {
    var command =
        new IngestEditorialReadingCommand(
            "Tale",
            "Content",
            "fr", // French is not in english-only policy
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "Desc",
            EditorialContentType.LEGEND,
            "FR",
            EditorialRegion.EUROPE,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            "fr-group",
            "cover",
            null,
            AccessTier.FREE,
            validCommand().questions());

    assertThatThrownBy(() -> useCase.ingest(command))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Content language 'fr' is not currently supported");
  }

  @Test
  void rejectsMissingAdaptationGroupKey() {
    var command =
        new IngestEditorialReadingCommand(
            "Tale",
            "Content",
            "en",
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "Desc",
            EditorialContentType.LEGEND,
            "US",
            EditorialRegion.NORTHERN_AMERICA,
            SourceKind.ORIGINAL_EDITORIAL,
            RightsStatus.ORIGINAL,
            AdaptationKind.ORIGINAL,
            null,
            null,
            null,
            null,
            null,
            "   ", // blank adaptation group key
            "cover",
            null,
            AccessTier.FREE,
            validCommand().questions());

    assertThatThrownBy(() -> useCase.ingest(command))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("adaptationGroupKey is required");
  }

  @Test
  void rejectsTranslatedAdaptationWithoutSourceLanguage() {
    var command =
        new IngestEditorialReadingCommand(
            "Tale",
            "Content",
            "en",
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "Desc",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.PUBLIC_DOMAIN,
            AdaptationKind.TRANSLATED_ADAPTATION,
            null, // missing sourceLanguage for translated adaptation
            null,
            null,
            null,
            null,
            "group-trans",
            "cover",
            null,
            AccessTier.FREE,
            validCommand().questions());

    assertThatThrownBy(() -> useCase.ingest(command))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Translated adaptation requires source language");
  }
}
