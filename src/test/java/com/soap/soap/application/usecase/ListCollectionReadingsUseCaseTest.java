package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.CollectionNotFoundException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.PlatformReadingRecommendationCalculator;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListCollectionReadingsUseCaseTest {

  @Mock private UserRepositoryPort users;
  @Mock private ReadingCollectionRepositoryPort collections;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private CurrentUserPort currentUser;

  private UUID userId;
  private ListCollectionReadingsUseCase useCase;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);
    useCase =
        new ListCollectionReadingsUseCase(
            users,
            collections,
            progress,
            vocabulary,
            new TextWordProcessor(),
            new PlatformReadingRecommendationCalculator(),
            currentUser);
  }

  @Test
  @DisplayName("Invokes findReadings with user's learning language 'fr'")
  void invokesFindReadingsWithUserLearningLanguageFrench() {
    var collectionKey = "french-classics";
    var request = new PageRequest(0, 10);
    var user =
        new User(
            userId,
            "Pierre",
            "pierre@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "pierre",
            28,
            "en",
            "fr");

    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey(collectionKey))
        .thenReturn(
            Optional.of(
                new ReadingCollection(
                    UUID.randomUUID(), collectionKey, "French Classics", "Desc", 1, true, null)));
    when(users.findById(userId)).thenReturn(Optional.of(user));

    var readingId = UUID.randomUUID();
    var reading =
        new Reading(
            readingId,
            null,
            "Le Petit Prince",
            "bonjour le monde",
            "fr",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.A1,
            "Fiction",
            EditorialStatus.PUBLISHED);

    when(collections.findReadings(collectionKey, "fr", request))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(vocabulary.findStatusesByNormalizedValues(eq(userId), eq("fr"), any()))
        .thenReturn(Map.of());
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    var result = useCase.listCollectionReadings(collectionKey, request);

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().getFirst().title()).isEqualTo("Le Petit Prince");
    assertThat(result.content().getFirst().language()).isEqualTo("fr");

    verify(collections).findReadings(collectionKey, "fr", request);
  }

  @Test
  @DisplayName("Canonicalizes learning language before calling repository (e.g. pt-br -> pt-BR)")
  void canonicalizesLearningLanguageBeforeQueryingRepository() {
    var collectionKey = "brazilian-stories";
    var request = new PageRequest(0, 5);
    var user =
        new User(
            userId,
            "Ana",
            "ana@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "ana",
            25,
            "en",
            "pt-br");

    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey(collectionKey))
        .thenReturn(
            Optional.of(
                new ReadingCollection(
                    UUID.randomUUID(), collectionKey, "Brazilian Stories", "Desc", 1, true, null)));
    when(users.findById(userId)).thenReturn(Optional.of(user));

    var readingId = UUID.randomUUID();
    var reading =
        new Reading(
            readingId,
            null,
            "Historias do Brasil",
            "ola mundo",
            "pt-BR",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Real Story",
            EditorialStatus.PUBLISHED);

    when(collections.findReadings(collectionKey, "pt-BR", request))
        .thenReturn(new PageResult<>(List.of(reading), 0, 5, 1));
    when(vocabulary.findStatusesByNormalizedValues(eq(userId), eq("pt-BR"), any()))
        .thenReturn(Map.of());
    when(progress.findByUserIdAndReadingIds(eq(userId), any())).thenReturn(Map.of());

    var result = useCase.listCollectionReadings(collectionKey, request);

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content().getFirst().language()).isEqualTo("pt-BR");
    verify(collections).findReadings(collectionKey, "pt-BR", request);
  }

  @Test
  @DisplayName("Throws IllegalStateException when user has no active learning language")
  void throwsIllegalStateExceptionWhenLearningLanguageMissing() {
    var collectionKey = "any-collection";
    var request = new PageRequest(0, 10);
    var userWithoutLanguage =
        new User(
            userId,
            "NoLang",
            "nolang@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "nolang",
            30,
            "es",
            null);

    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey(collectionKey))
        .thenReturn(
            Optional.of(
                new ReadingCollection(
                    UUID.randomUUID(), collectionKey, "Any", "Desc", 1, true, null)));
    when(users.findById(userId)).thenReturn(Optional.of(userWithoutLanguage));

    assertThatThrownBy(() -> useCase.listCollectionReadings(collectionKey, request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have an active learning language configured");

    verify(collections, never()).findReadings(any(), any(), any());
    verify(collections, never()).findReadings(any(), any());
  }

  @Test
  @DisplayName("Throws IllegalStateException when user's learning language is blank")
  void throwsIllegalStateExceptionWhenLearningLanguageBlank() {
    var collectionKey = "any-collection";
    var request = new PageRequest(0, 10);
    var userWithBlankLanguage =
        new User(
            userId,
            "BlankLang",
            "blank@example.com",
            "hash",
            LocalDateTime.now(),
            true,
            "blank",
            30,
            "es",
            "   ");

    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey(collectionKey))
        .thenReturn(
            Optional.of(
                new ReadingCollection(
                    UUID.randomUUID(), collectionKey, "Any", "Desc", 1, true, null)));
    when(users.findById(userId)).thenReturn(Optional.of(userWithBlankLanguage));

    assertThatThrownBy(() -> useCase.listCollectionReadings(collectionKey, request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have an active learning language configured");

    verify(collections, never()).findReadings(any(), any(), any());
  }

  @Test
  @DisplayName("Throws UserNotFoundException when user does not exist in users.existsById")
  void throwsUserNotFoundExceptionWhenUserDoesNotExist() {
    when(users.existsById(userId)).thenReturn(false);

    assertThatThrownBy(() -> useCase.listCollectionReadings("any", new PageRequest(0, 10)))
        .isInstanceOf(UserNotFoundException.class);

    verify(collections, never()).findReadings(any(), any(), any());
  }

  @Test
  @DisplayName("Throws CollectionNotFoundException when collection is inactive or missing")
  void throwsCollectionNotFoundExceptionWhenCollectionMissing() {
    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey("non-existent")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.listCollectionReadings("non-existent", new PageRequest(0, 10)))
        .isInstanceOf(CollectionNotFoundException.class);

    verify(collections, never()).findReadings(any(), any(), any());
  }
}
