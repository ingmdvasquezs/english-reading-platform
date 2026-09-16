package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.CollectionNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.PlatformReadingPersonalizationService;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionUseCasesTest {
  @Mock private UserRepositoryPort users;
  @Mock private ReadingCollectionRepositoryPort collections;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private PlatformReadingPersonalizationService personalizer;
  @Mock private CurrentUserPort currentUser;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);
  }

  @Test
  void listsOnlyTheActiveCollectionsReturnedByPersistenceInEditorialOrder() {
    var first = collection("everyday", 1, null);
    var second = collection("mysteries", 2, "optional-cover");
    when(users.existsById(userId)).thenReturn(true);
    when(collections.findAllActive()).thenReturn(List.of(first, second));

    var result = new ListCollectionsUseCase(users, collections, currentUser).listCollections();

    assertThat(result).containsExactly(first, second);
    assertThat(result.getFirst().coverKey()).isNull();
  }

  @Test
  void rejectsAnUnknownOrInactiveCollectionBeforeQueryingMemberships() {
    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey("missing")).thenReturn(Optional.empty());
    var useCase = useCase();

    assertThatThrownBy(() -> useCase.listCollectionReadings("missing", new PageRequest(0, 10)))
        .isInstanceOf(CollectionNotFoundException.class);
    verify(collections, never()).findReadings(any(), any(), any());
  }

  @Test
  void preservesDatabasePaginationAndAddsProgressForTheAuthenticatedUserOnly() {
    var readingId = UUID.randomUUID();
    var secondReadingId = UUID.randomUUID();
    var reading = reading(readingId, "First", "known learning new ignored unknown");
    var secondReading = reading(secondReadingId, "Second", "known");
    var request = new PageRequest(1, 2);
    when(users.existsById(userId)).thenReturn(true);
    when(users.findById(userId))
        .thenReturn(Optional.of(new User(userId, "Alice", "alice@example.com")));
    when(collections.findActiveByKey("everyday"))
        .thenReturn(Optional.of(collection("everyday", 1, null)));
    when(collections.findReadings("everyday", "en", request))
        .thenReturn(new PageResult<>(List.of(reading, secondReading), 1, 2, 11));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(readingId, secondReadingId)))
        .thenReturn(
            Map.of(
                readingId,
                ReadingProgress.completed(
                    userId, readingId, LocalDateTime.now(), LocalDateTime.now())));

    var p1 =
        new RecommendedPlatformReading(
            readingId,
            "First",
            "en",
            EditorialLevel.A1,
            "Daily Life",
            reading.createdAt(),
            5,
            1,
            1,
            1,
            1,
            1,
            new java.math.BigDecimal("56.00"),
            new java.math.BigDecimal("80.00"),
            ReadingProgressStatus.COMPLETED,
            null,
            RecommendationReasonCode.DISCOVERY,
            null);
    var p2 =
        new RecommendedPlatformReading(
            secondReadingId,
            "Second",
            "en",
            EditorialLevel.A1,
            "Daily Life",
            secondReading.createdAt(),
            1,
            1,
            0,
            0,
            0,
            0,
            new java.math.BigDecimal("100.00"),
            new java.math.BigDecimal("100.00"),
            null,
            null,
            RecommendationReasonCode.HIGH_VOCABULARY_MATCH,
            null);

    when(personalizer.personalizeReadings(
            eq(userId), eq("en"), eq(List.of(reading, secondReading)), any()))
        .thenReturn(List.of(p1, p2));

    var result = useCase().listCollectionReadings("everyday", request);

    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.totalElements()).isEqualTo(11);
    assertThat(result.content()).extracting("title").containsExactly("First", "Second");
    assertThat(result.content().getFirst().progressStatus())
        .isEqualTo(ReadingProgressStatus.COMPLETED);
    assertThat(result.content().getFirst())
        .satisfies(
            item -> {
              assertThat(item.uniqueWords()).isEqualTo(5);
              assertThat(item.knownWords()).isEqualTo(1);
              assertThat(item.learningWords()).isEqualTo(1);
              assertThat(item.explicitNewWords()).isEqualTo(1);
              assertThat(item.ignoredWords()).isEqualTo(1);
              assertThat(item.unclassifiedWords()).isEqualTo(1);
              assertThat(item.vocabularyFitPercentage()).isEqualByComparingTo("56.00");
              assertThat(item.classificationConfidencePercentage()).isEqualByComparingTo("80.00");
            });
    verify(progress).findByUserIdAndReadingIds(userId, Set.of(readingId, secondReadingId));
    verify(personalizer)
        .personalizeReadings(
            eq(userId),
            eq("en"),
            eq(List.of(reading, secondReading)),
            eq(Map.of(readingId, ReadingProgressStatus.COMPLETED)));
  }

  @Test
  void calculatesDifferentMetricsForTheSameReadingFromEachUsersBatchVocabulary() {
    var secondUserId = UUID.randomUUID();
    var reading = reading(UUID.randomUUID(), "Shared", "alpha beta");
    var request = new PageRequest(0, 10);
    when(currentUser.requireUserId()).thenReturn(userId, secondUserId);
    when(users.existsById(userId)).thenReturn(true);
    when(users.existsById(secondUserId)).thenReturn(true);
    when(users.findById(userId))
        .thenReturn(Optional.of(new User(userId, "User1", "u1@example.com")));
    when(users.findById(secondUserId))
        .thenReturn(Optional.of(new User(secondUserId, "User2", "u2@example.com")));
    when(collections.findActiveByKey("shared"))
        .thenReturn(Optional.of(collection("shared", 1, null)));
    when(collections.findReadings("shared", "en", request))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());
    when(progress.findByUserIdAndReadingIds(secondUserId, Set.of(reading.id())))
        .thenReturn(Map.of());

    var firstSummary =
        new RecommendedPlatformReading(
            reading.id(),
            "Shared",
            "en",
            EditorialLevel.A1,
            "Daily Life",
            reading.createdAt(),
            2,
            1,
            0,
            0,
            0,
            1,
            new java.math.BigDecimal("50.00"),
            new java.math.BigDecimal("50.00"),
            null,
            null,
            RecommendationReasonCode.DISCOVERY,
            null);
    var secondSummary =
        new RecommendedPlatformReading(
            reading.id(),
            "Shared",
            "en",
            EditorialLevel.A1,
            "Daily Life",
            reading.createdAt(),
            2,
            0,
            1,
            0,
            0,
            1,
            new java.math.BigDecimal("0.00"),
            new java.math.BigDecimal("50.00"),
            null,
            null,
            RecommendationReasonCode.DISCOVERY,
            null);

    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(firstSummary));
    when(personalizer.personalizeReadings(eq(secondUserId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(secondSummary));

    var first = useCase().listCollectionReadings("shared", request).content().getFirst();
    var second = useCase().listCollectionReadings("shared", request).content().getFirst();

    assertThat(first.knownWords()).isEqualTo(1);
    assertThat(first.learningWords()).isZero();
    assertThat(second.knownWords()).isZero();
    assertThat(second.learningWords()).isEqualTo(1);
    verify(personalizer).personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any());
    verify(personalizer)
        .personalizeReadings(eq(secondUserId), eq("en"), eq(List.of(reading)), any());
  }

  private ListCollectionReadingsUseCase useCase() {
    return new ListCollectionReadingsUseCase(
        users, collections, progress, personalizer, currentUser);
  }

  private Reading reading(UUID id, String title, String content) {
    return new Reading(
        id,
        null,
        title,
        content,
        "en",
        LocalDateTime.parse("2026-09-01T08:00:00"),
        ReadingOrigin.PLATFORM,
        EditorialLevel.A1,
        "Daily Life",
        com.soap.soap.domain.model.EditorialStatus.PUBLISHED);
  }

  private ReadingCollection collection(String key, int order, String coverKey) {
    return new ReadingCollection(UUID.randomUUID(), key, key, "Description", order, true, coverKey);
  }
}
