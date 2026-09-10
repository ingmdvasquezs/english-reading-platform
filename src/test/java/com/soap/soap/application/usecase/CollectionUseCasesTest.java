package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.CollectionNotFoundException;
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
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
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
  @Mock private UserVocabularyRepositoryPort vocabulary;
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
    verify(collections, never()).findReadings("missing", new PageRequest(0, 10));
  }

  @Test
  void preservesDatabasePaginationAndAddsProgressForTheAuthenticatedUserOnly() {
    var readingId = UUID.randomUUID();
    var secondReadingId = UUID.randomUUID();
    var reading = reading(readingId, "First", "known learning new ignored unknown");
    var secondReading = reading(secondReadingId, "Second", "known");
    var request = new PageRequest(1, 2);
    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey("everyday"))
        .thenReturn(Optional.of(collection("everyday", 1, null)));
    when(collections.findReadings("everyday", request))
        .thenReturn(new PageResult<>(List.of(reading, secondReading), 1, 2, 11));
    when(vocabulary.findStatusesByNormalizedValues(
            userId, "en", Set.of("known", "learning", "new", "ignored", "unknown")))
        .thenReturn(
            Map.of(
                "known", com.soap.soap.domain.model.VocabularyStatus.KNOWN,
                "learning", com.soap.soap.domain.model.VocabularyStatus.LEARNING,
                "new", com.soap.soap.domain.model.VocabularyStatus.NEW,
                "ignored", com.soap.soap.domain.model.VocabularyStatus.IGNORED));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(readingId, secondReadingId)))
        .thenReturn(
            Map.of(
                readingId,
                ReadingProgress.completed(
                    userId, readingId, LocalDateTime.now(), LocalDateTime.now())));

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
    verify(vocabulary)
        .findStatusesByNormalizedValues(
            userId, "en", Set.of("known", "learning", "new", "ignored", "unknown"));
    verify(progress).findByUserIdAndReadingIds(userId, Set.of(readingId, secondReadingId));
  }

  @Test
  void calculatesDifferentMetricsForTheSameReadingFromEachUsersBatchVocabulary() {
    var secondUserId = UUID.randomUUID();
    var reading = reading(UUID.randomUUID(), "Shared", "alpha beta");
    var request = new PageRequest(0, 10);
    when(currentUser.requireUserId()).thenReturn(userId, secondUserId);
    when(users.existsById(userId)).thenReturn(true);
    when(users.existsById(secondUserId)).thenReturn(true);
    when(collections.findActiveByKey("shared"))
        .thenReturn(Optional.of(collection("shared", 1, null)));
    when(collections.findReadings("shared", request))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(vocabulary.findStatusesByNormalizedValues(userId, "en", Set.of("alpha", "beta")))
        .thenReturn(Map.of("alpha", com.soap.soap.domain.model.VocabularyStatus.KNOWN));
    when(vocabulary.findStatusesByNormalizedValues(secondUserId, "en", Set.of("alpha", "beta")))
        .thenReturn(Map.of("alpha", com.soap.soap.domain.model.VocabularyStatus.LEARNING));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());
    when(progress.findByUserIdAndReadingIds(secondUserId, Set.of(reading.id())))
        .thenReturn(Map.of());

    var first = useCase().listCollectionReadings("shared", request).content().getFirst();
    var second = useCase().listCollectionReadings("shared", request).content().getFirst();

    assertThat(first.knownWords()).isEqualTo(1);
    assertThat(first.learningWords()).isZero();
    assertThat(second.knownWords()).isZero();
    assertThat(second.learningWords()).isEqualTo(1);
    verify(vocabulary).findStatusesByNormalizedValues(userId, "en", Set.of("alpha", "beta"));
    verify(vocabulary).findStatusesByNormalizedValues(secondUserId, "en", Set.of("alpha", "beta"));
  }

  private ListCollectionReadingsUseCase useCase() {
    return new ListCollectionReadingsUseCase(
        users,
        collections,
        progress,
        vocabulary,
        new TextWordProcessor(),
        new PlatformReadingRecommendationCalculator(),
        currentUser);
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
        "Daily Life");
  }

  private ReadingCollection collection(String key, int order, String coverKey) {
    return new ReadingCollection(UUID.randomUUID(), key, key, "Description", order, true, coverKey);
  }
}
