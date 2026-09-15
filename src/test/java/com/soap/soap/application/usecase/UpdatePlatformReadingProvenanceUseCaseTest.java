package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.UpdatePlatformReadingProvenanceCommand;
import com.soap.soap.application.exception.EditorialProvenanceUpdateException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdatePlatformReadingProvenanceUseCaseTest {

  @Mock private ReadingRepositoryPort readings;

  private UpdatePlatformReadingProvenanceUseCase useCase;

  private UUID readingId;
  private LocalDateTime createdAt;
  private Reading existingPublishedPlatformReading;

  @BeforeEach
  void setUp() {
    useCase = new UpdatePlatformReadingProvenanceUseCase(readings);

    readingId = UUID.randomUUID();
    createdAt = LocalDateTime.now().minusDays(5);

    existingPublishedPlatformReading =
        new Reading(
            readingId,
            null,
            "The Mohán and the Spring of Water",
            "Long ago in Pasuncha...",
            LanguageTag.of("en"),
            createdAt,
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Culture, Arts & Fiction",
            "mohan-pasuncha",
            EditorialStatus.PUBLISHED,
            "A woman seeks water...",
            EditorialContentType.LEGEND,
            "CO",
            EditorialRegion.SOUTH_AMERICA,
            SourceKind.ORAL_TRADITION,
            RightsStatus.ORIGINAL,
            AdaptationKind.PEDAGOGICAL_ADAPTATION,
            LanguageTag.of("es"),
            "Old Title",
            "Old Author",
            "https://old-url.com",
            "Old notes",
            "mohan-pasuncha",
            null,
            AccessTier.FREE);
  }

  @Test
  void updatesProvenanceSuccessfullyForPublishedPlatformReadingByAdaptationKey() {
    when(readings.findPlatformReadingByAdaptationKey("mohan-pasuncha", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(existingPublishedPlatformReading));
    when(readings.save(any(Reading.class))).thenAnswer(inv -> inv.getArgument(0));

    var command =
        new UpdatePlatformReadingProvenanceCommand(
            "mohan-pasuncha",
            "en",
            EditorialLevel.B1,
            "Cuento del Mohán",
            "Comunidad de Pasuncha",
            "https://www.bibliotecanacional.gov.co/es-co/Bibliotecas-en-Red/cuento-del-mohan.html",
            "Verified Pasuncha oral lore.");

    var result = useCase.updateProvenance(command);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(readingId);
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);
    assertThat(result.content()).isEqualTo("Long ago in Pasuncha...");
    assertThat(result.title()).isEqualTo("The Mohán and the Spring of Water");
    assertThat(result.origin()).isEqualTo(ReadingOrigin.PLATFORM);
    assertThat(result.editorialLevel()).isEqualTo(EditorialLevel.B1);
    assertThat(result.coverKey()).isEqualTo("mohan-pasuncha");
    assertThat(result.rightsStatus()).isEqualTo(RightsStatus.ORIGINAL);
    assertThat(result.adaptationKind()).isEqualTo(AdaptationKind.PEDAGOGICAL_ADAPTATION);

    assertThat(result.sourceTitle()).isEqualTo("Cuento del Mohán");
    assertThat(result.sourceAuthor()).isEqualTo("Comunidad de Pasuncha");
    assertThat(result.sourceUrl())
        .isEqualTo(
            "https://www.bibliotecanacional.gov.co/es-co/Bibliotecas-en-Red/cuento-del-mohan.html");
    assertThat(result.sourceNotes()).isEqualTo("Verified Pasuncha oral lore.");

    var captor = ArgumentCaptor.forClass(Reading.class);
    verify(readings).save(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.id()).isEqualTo(readingId);
    assertThat(saved.createdAt()).isEqualTo(createdAt);
    assertThat(saved.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);
    assertThat(saved.sourceTitle()).isEqualTo("Cuento del Mohán");
  }

  @Test
  void rejectsProvenanceUpdateWhenReadingIsUserOrigin() {
    var userReading =
        new Reading(
            readingId,
            mock(User.class),
            "My User Reading",
            "User content",
            LanguageTag.of("en"),
            createdAt,
            ReadingOrigin.USER,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(readings.findById(readingId)).thenReturn(Optional.of(userReading));

    var command =
        new UpdatePlatformReadingProvenanceCommand(
            readingId,
            null,
            null,
            null,
            "New Title",
            "New Author",
            "https://example.com",
            "New notes");

    assertThatThrownBy(() -> useCase.updateProvenance(command))
        .isInstanceOf(EditorialProvenanceUpdateException.class)
        .hasMessageContaining("Only PLATFORM readings can have provenance updated");
  }

  @Test
  void rejectsWhenReadingIsNotFoundByKey() {
    when(readings.findPlatformReadingByAdaptationKey("unknown-key", "en", EditorialLevel.B2))
        .thenReturn(Optional.empty());

    var command =
        new UpdatePlatformReadingProvenanceCommand(
            "unknown-key", "en", EditorialLevel.B2, "Title", null, null, null);

    assertThatThrownBy(() -> useCase.updateProvenance(command))
        .isInstanceOf(EditorialProvenanceUpdateException.class)
        .hasMessageContaining("Platform reading not found for key");
  }

  @Test
  void rejectsWhenReadingIsNotFoundById() {
    var id = UUID.randomUUID();
    when(readings.findById(id)).thenReturn(Optional.empty());

    var command =
        new UpdatePlatformReadingProvenanceCommand(id, null, null, null, "Title", null, null, null);

    assertThatThrownBy(() -> useCase.updateProvenance(command))
        .isInstanceOf(ReadingNotFoundException.class);
  }

  @Test
  void rejectsWhenCommandIsNull() {
    assertThatThrownBy(() -> useCase.updateProvenance(null))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Provenance update command must not be null");
  }

  @Test
  void rejectsWhenKeyIsBlank() {
    var command =
        new UpdatePlatformReadingProvenanceCommand(
            "  ", "en", EditorialLevel.B1, "Title", null, null, null);

    assertThatThrownBy(() -> useCase.updateProvenance(command))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Adaptation group key must not be blank");
  }
}
