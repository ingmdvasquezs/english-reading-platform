package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.EditorialCollectionReadingItemCommand;
import com.soap.soap.application.command.ImportEditorialCollectionCommand;
import com.soap.soap.application.exception.EditorialCollectionImportException;
import com.soap.soap.application.model.CollectionMembershipItem;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImportEditorialCollectionUseCaseTest {

  private ReadingCollectionRepositoryPort collections;
  private ReadingRepositoryPort readings;
  private ImportEditorialCollectionUseCase useCase;

  @BeforeEach
  void setUp() {
    collections = mock(ReadingCollectionRepositoryPort.class);
    readings = mock(ReadingRepositoryPort.class);
    useCase = new ImportEditorialCollectionUseCase(collections, readings);
  }

  private Reading mockReading(UUID id, String title, EditorialLevel level, EditorialStatus status) {
    return new Reading(
        id,
        null,
        title,
        "Content",
        LanguageTag.of("en"),
        LocalDateTime.now(),
        ReadingOrigin.PLATFORM,
        level,
        "Culture",
        "cover-key",
        status,
        "A short description",
        EditorialContentType.FICTION,
        "CO",
        EditorialRegion.SOUTH_AMERICA,
        SourceKind.ORAL_TRADITION,
        RightsStatus.ORIGINAL,
        AdaptationKind.ORIGINAL,
        null,
        "Source",
        "Author",
        null,
        null,
        "group-key",
        null,
        AccessTier.FREE);
  }

  private Reading mockReading(UUID id, String title, EditorialLevel level) {
    return mockReading(id, title, level, EditorialStatus.PUBLISHED);
  }

  @Test
  @DisplayName("Imports a new collection and creates memberships")
  void importsNewCollectionSuccessfully() {
    var readingId1 = UUID.randomUUID();
    var readingId2 = UUID.randomUUID();
    var r1 = mockReading(readingId1, "Reading 1", EditorialLevel.B1);
    var r2 = mockReading(readingId2, "Reading 2", EditorialLevel.B2);

    when(readings.findPlatformReadingByAdaptationKey("group-1", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(r1));
    when(readings.findPlatformReadingByAdaptationKey("group-2", "en", EditorialLevel.B2))
        .thenReturn(Optional.of(r2));
    when(collections.findByKey("myths-colombia")).thenReturn(Optional.empty());

    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia",
            "Mitos de Colombia",
            "Relatos de tradicion oral",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand("group-1", "en", EditorialLevel.B1, 1),
                new EditorialCollectionReadingItemCommand("group-2", "en", EditorialLevel.B2, 2)));

    var result = useCase.importCollection(command);

    assertThat(result.created()).isTrue();
    assertThat(result.key()).isEqualTo("myths-colombia");
    assertThat(result.title()).isEqualTo("Mitos de Colombia");
    assertThat(result.membershipsCount()).isEqualTo(2);

    verify(collections).save(any(ReadingCollection.class));
    verify(collections)
        .replaceMemberships(
            eq(result.collectionId()),
            eq(
                List.of(
                    new CollectionMembershipItem(readingId1, 1),
                    new CollectionMembershipItem(readingId2, 2))));
  }

  @Test
  @DisplayName("Re-importing is idempotent and preserves existing collection UUID")
  void reimportIsIdempotent() {
    var existingId = UUID.randomUUID();
    var existingCollection =
        new ReadingCollection(existingId, "myths-colombia", "Old Title", "Old Desc", 1, true, null);

    var readingId = UUID.randomUUID();
    var r = mockReading(readingId, "Reading 1", EditorialLevel.B1);

    when(readings.findPlatformReadingByAdaptationKey("group-1", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(r));
    when(collections.findByKey("myths-colombia")).thenReturn(Optional.of(existingCollection));

    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia",
            "Mitos de Colombia Updated",
            "Updated Description",
            1,
            true,
            "cover-key",
            List.of(
                new EditorialCollectionReadingItemCommand("group-1", "en", EditorialLevel.B1, 1)));

    var result = useCase.importCollection(command);

    assertThat(result.created()).isFalse();
    assertThat(result.collectionId()).isEqualTo(existingId);
    assertThat(result.title()).isEqualTo("Mitos de Colombia Updated");
    assertThat(result.membershipsCount()).isEqualTo(1);

    verify(collections)
        .save(
            eq(
                new ReadingCollection(
                    existingId,
                    "myths-colombia",
                    "Mitos de Colombia Updated",
                    "Updated Description",
                    1,
                    true,
                    "cover-key")));
  }

  @Test
  @DisplayName("Fails when reading is not found")
  void failsWhenReadingNotFound() {
    when(readings.findPlatformReadingByAdaptationKey("unknown", "en", EditorialLevel.B1))
        .thenReturn(Optional.empty());

    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia",
            "Mitos",
            "Desc",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand("unknown", "en", EditorialLevel.B1, 1)));

    assertThatThrownBy(() -> useCase.importCollection(command))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("Platform reading not found");

    verify(collections, never()).save(any());
  }

  @Test
  @DisplayName("Fails when reading is not PUBLISHED")
  void failsWhenReadingNotPublished() {
    var reading =
        mockReading(UUID.randomUUID(), "Draft Reading", EditorialLevel.B1, EditorialStatus.DRAFT);

    when(readings.findPlatformReadingByAdaptationKey("group-1", "en", EditorialLevel.B1))
        .thenReturn(Optional.of(reading));

    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia",
            "Mitos",
            "Desc",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand("group-1", "en", EditorialLevel.B1, 1)));

    assertThatThrownBy(() -> useCase.importCollection(command))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("is not PUBLISHED");

    verify(collections, never()).save(any());
  }

  @Test
  @DisplayName("Fails on duplicate reading editorial identity")
  void failsOnDuplicateReadingIdentity() {
    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia",
            "Mitos",
            "Desc",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand("group-1", "en", EditorialLevel.B1, 1),
                new EditorialCollectionReadingItemCommand("group-1", "en", EditorialLevel.B1, 2)));

    assertThatThrownBy(() -> useCase.importCollection(command))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("Duplicate reading identity");

    verify(collections, never()).save(any());
  }

  @Test
  @DisplayName("Fails on duplicate displayOrder in readings")
  void failsOnDuplicateDisplayOrder() {
    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia",
            "Mitos",
            "Desc",
            1,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand("group-1", "en", EditorialLevel.B1, 1),
                new EditorialCollectionReadingItemCommand("group-2", "en", EditorialLevel.B2, 1)));

    assertThatThrownBy(() -> useCase.importCollection(command))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("Duplicate reading displayOrder");

    verify(collections, never()).save(any());
  }

  @Test
  @DisplayName("Fails on invalid displayOrder (<= 0)")
  void failsOnInvalidDisplayOrder() {
    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia",
            "Mitos",
            "Desc",
            0,
            true,
            null,
            List.of(
                new EditorialCollectionReadingItemCommand("group-1", "en", EditorialLevel.B1, 1)));

    assertThatThrownBy(() -> useCase.importCollection(command))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("displayOrder must be greater than 0");

    verify(collections, never()).save(any());
  }

  @Test
  @DisplayName("Fails on empty readings")
  void failsOnEmptyReadings() {
    var command =
        new ImportEditorialCollectionCommand(
            "myths-colombia", "Mitos", "Desc", 1, true, null, List.of());

    assertThatThrownBy(() -> useCase.importCollection(command))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("at least one reading");

    verify(collections, never()).save(any());
  }
}
