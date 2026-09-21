package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.CollectionNotFoundException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.PlatformReadingPersonalizationService;
import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.PlatformReadingSort;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RecommendationReasonCode;
import com.soap.soap.domain.model.User;
import java.math.BigDecimal;
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
class BrowsePlatformReadingsUseCaseTest {

  @Mock private UserRepositoryPort users;
  @Mock private ReadingCollectionRepositoryPort collections;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private PlatformReadingPersonalizationService personalizer;
  @Mock private CurrentUserPort currentUser;

  private BrowsePlatformReadingsUseCase useCase;
  private UUID userId;

  @BeforeEach
  void setUp() {
    useCase =
        new BrowsePlatformReadingsUseCase(
            users, collections, readings, progress, personalizer, currentUser);
    userId = UUID.randomUUID();
    lenient().when(currentUser.requireUserId()).thenReturn(userId);
  }

  private void mockUser(String learningLanguage) {
    when(users.existsById(userId)).thenReturn(true);
    when(users.findById(userId))
        .thenReturn(
            Optional.of(
                new User(
                    userId,
                    "Reader",
                    "reader@example.com",
                    "hash",
                    LocalDateTime.now(),
                    false,
                    null,
                    25,
                    "es",
                    learningLanguage)));
  }

  private Reading platformReading(UUID id, String title, EditorialLevel level, String category) {
    return new Reading(
        id,
        null,
        title,
        "Sample content",
        "en",
        LocalDateTime.now(),
        ReadingOrigin.PLATFORM,
        level,
        category,
        "cover-key-" + title.toLowerCase(),
        EditorialStatus.PUBLISHED);
  }

  private RecommendedPlatformReading recommendedReading(Reading reading, BigDecimal fit) {
    return new RecommendedPlatformReading(
        reading.id(),
        reading.title(),
        reading.language().value(),
        reading.editorialLevel(),
        reading.category(),
        reading.createdAt(),
        100,
        80,
        15,
        5,
        0,
        0,
        fit,
        new BigDecimal("95.00"),
        ReadingProgressStatus.IN_PROGRESS,
        reading.coverKey(),
        RecommendationReasonCode.BALANCED_CHALLENGE,
        reading.shortDescription(),
        reading.countryCode(),
        reading.discoveryTopic());
  }

  @Test
  void throwsExceptionWhenQueryOrPageRequestIsNull() {
    assertThatThrownBy(() -> useCase.browsePlatformReadings(null))
        .isInstanceOf(InvalidApplicationArgumentException.class);

    assertThatThrownBy(
            () ->
                useCase.browsePlatformReadings(
                    new BrowsePlatformReadingsQuery(null, null, null, null, null, null)))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }

  @Test
  void throwsExceptionWhenCountryCodeIsInvalid() {
    mockUser("en");

    assertThatThrownBy(
            () ->
                useCase.browsePlatformReadings(
                    new BrowsePlatformReadingsQuery(
                        null, null, null, "colombia", null, new PageRequest(0, 10))))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Country code must be 2 uppercase ISO letters");

    assertThatThrownBy(
            () ->
                useCase.browsePlatformReadings(
                    new BrowsePlatformReadingsQuery(
                        null, null, null, "co", null, new PageRequest(0, 10))))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("Country code must be 2 uppercase ISO letters");
  }

  @Test
  void throwsExceptionWhenUserNotFound() {
    when(users.existsById(userId)).thenReturn(false);

    assertThatThrownBy(
            () ->
                useCase.browsePlatformReadings(
                    new BrowsePlatformReadingsQuery(
                        null, null, null, null, null, new PageRequest(0, 10))))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void throwsExceptionWhenCollectionNotFoundOrInactive() {
    when(users.existsById(userId)).thenReturn(true);
    when(collections.findActiveByKey("unknown-collection")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                useCase.browsePlatformReadings(
                    new BrowsePlatformReadingsQuery(
                        "unknown-collection", null, null, null, null, new PageRequest(0, 10))))
        .isInstanceOf(CollectionNotFoundException.class);
  }

  @Test
  void browsesWithoutFiltersSuccessfullyWithBatchPersonalization() {
    mockUser("en");
    var reading1 =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");
    var reading2 =
        platformReading(
            UUID.randomUUID(), "Candileja", EditorialLevel.B2, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            null,
            null,
            null,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading1, reading2), 0, 10, 2));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading1.id(), reading2.id())))
        .thenReturn(
            Map.of(
                reading1.id(),
                new ReadingProgress(
                    UUID.randomUUID(),
                    userId,
                    reading1.id(),
                    ReadingProgressStatus.IN_PROGRESS,
                    LocalDateTime.now(),
                    null,
                    2,
                    1)));

    var rec1 = recommendedReading(reading1, new BigDecimal("85.50"));
    var rec2 = recommendedReading(reading2, new BigDecimal("72.00"));

    when(personalizer.personalizeReadings(
            eq(userId), eq("en"), eq(List.of(reading1, reading2)), any()))
        .thenReturn(List.of(rec1, rec2));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, null, null, null, new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec1, rec2);
    assertThat(result.totalElements()).isEqualTo(2);
    assertThat(result.page()).isEqualTo(0);
    assertThat(result.size()).isEqualTo(10);
    verify(personalizer)
        .personalizeReadings(eq(userId), eq("en"), eq(List.of(reading1, reading2)), any());
  }

  @Test
  void filtersByCategoryOnly() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            null,
            "Culture, Arts & Fiction",
            null,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());

    var rec = recommendedReading(reading, new BigDecimal("88.00"));
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(rec));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null,
                EditorialCategory.CULTURE_ARTS_AND_FICTION,
                null,
                null,
                null,
                new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec);
    verify(readings)
        .browsePlatformReadings(
            null,
            "Culture, Arts & Fiction",
            null,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void filtersByEditorialLevelOnly() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            null,
            null,
            EditorialLevel.B1,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());

    var rec = recommendedReading(reading, new BigDecimal("88.00"));
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(rec));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, null, EditorialLevel.B1, null, null, new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec);
    verify(readings)
        .browsePlatformReadings(
            null,
            null,
            EditorialLevel.B1,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void filtersByCountryCodeOnly() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            null,
            null,
            null,
            "CO",
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());

    var rec = recommendedReading(reading, new BigDecimal("88.00"));
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(rec));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, null, "CO", null, new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec);
    verify(readings)
        .browsePlatformReadings(
            null,
            null,
            null,
            "CO",
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void filtersByDiscoveryTopicOnly() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            null,
            null,
            null,
            null,
            DiscoveryTopic.MYTHS_AND_LEGENDS,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());

    var rec = recommendedReading(reading, new BigDecimal("88.00"));
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(rec));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, null, null, null, DiscoveryTopic.MYTHS_AND_LEGENDS, new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec);
    verify(readings)
        .browsePlatformReadings(
            null,
            null,
            null,
            null,
            DiscoveryTopic.MYTHS_AND_LEGENDS,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void filtersByCountryAndTopicCombined() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            null,
            null,
            null,
            "CO",
            DiscoveryTopic.MYTHS_AND_LEGENDS,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());

    var rec = recommendedReading(reading, new BigDecimal("88.00"));
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(rec));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, null, null, "CO", DiscoveryTopic.MYTHS_AND_LEGENDS, new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec);
    verify(readings)
        .browsePlatformReadings(
            null,
            null,
            null,
            "CO",
            DiscoveryTopic.MYTHS_AND_LEGENDS,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void filtersByCategoryAndLevelCombined() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            null,
            "Culture, Arts & Fiction",
            EditorialLevel.B1,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());

    var rec = recommendedReading(reading, new BigDecimal("88.00"));
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(rec));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null,
                EditorialCategory.CULTURE_ARTS_AND_FICTION,
                EditorialLevel.B1,
                null,
                null,
                new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec);
    verify(readings)
        .browsePlatformReadings(
            null,
            "Culture, Arts & Fiction",
            EditorialLevel.B1,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void filtersByCollectionAndLevelCombined() {
    mockUser("en");
    when(collections.findActiveByKey("colombian-myths-legends"))
        .thenReturn(
            Optional.of(
                new ReadingCollection(
                    UUID.randomUUID(),
                    "colombian-myths-legends",
                    "Colombian Myths",
                    "Description",
                    1,
                    true,
                    "cover-key")));

    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");

    when(readings.browsePlatformReadings(
            "colombian-myths-legends",
            null,
            EditorialLevel.B1,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());

    var rec = recommendedReading(reading, new BigDecimal("88.00"));
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(rec));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                "colombian-myths-legends",
                null,
                EditorialLevel.B1,
                null,
                null,
                new PageRequest(0, 10)));

    assertThat(result.content()).containsExactly(rec);
    verify(readings)
        .browsePlatformReadings(
            "colombian-myths-legends",
            null,
            EditorialLevel.B1,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void returnsEmptyPageImmediatelyWhenNoReadingsMatch() {
    mockUser("en");
    when(readings.browsePlatformReadings(
            null,
            null,
            EditorialLevel.A1,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(), 0, 10, 0));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, null, EditorialLevel.A1, null, null, new PageRequest(0, 10)));

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isZero();
    verify(progress, never()).findByUserIdAndReadingIds(any(), any());
    verify(personalizer, never()).personalizeReadings(any(), any(), any(), any());
  }

  @Test
  void queryCountRemainsConstantRegardlessOfPageSize() {
    mockUser("en");
    // Test size 5
    var reading5 =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");
    when(readings.browsePlatformReadings(
            null, null, null, null, null, PlatformReadingSort.DEFAULT, "en", new PageRequest(0, 5)))
        .thenReturn(new PageResult<>(List.of(reading5), 0, 5, 15));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading5.id()))).thenReturn(Map.of());
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading5)), any()))
        .thenReturn(List.of(recommendedReading(reading5, new BigDecimal("90.00"))));

    var res5 =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, null, null, null, new PageRequest(0, 5)));
    assertThat(res5.content()).hasSize(1);

    // Exactly 1 call to readings, 1 to progress, 1 to personalizer
    verify(readings)
        .browsePlatformReadings(
            null, null, null, null, null, PlatformReadingSort.DEFAULT, "en", new PageRequest(0, 5));
    verify(progress).findByUserIdAndReadingIds(userId, Set.of(reading5.id()));
    verify(personalizer).personalizeReadings(eq(userId), eq("en"), eq(List.of(reading5)), any());
  }

  @Test
  void passesExplicitSortCreatedAtDescToRepository() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");
    when(readings.browsePlatformReadings(
            null,
            null,
            null,
            null,
            null,
            PlatformReadingSort.CREATED_AT_DESC,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(recommendedReading(reading, new BigDecimal("90.00"))));

    var result =
        useCase.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null,
                null,
                null,
                null,
                null,
                PlatformReadingSort.CREATED_AT_DESC,
                new PageRequest(0, 10)));

    assertThat(result.content()).hasSize(1);
    verify(readings)
        .browsePlatformReadings(
            null,
            null,
            null,
            null,
            null,
            PlatformReadingSort.CREATED_AT_DESC,
            "en",
            new PageRequest(0, 10));
  }

  @Test
  void defaultsSortToDefaultWhenSortIsNullInQuery() {
    mockUser("en");
    var reading =
        platformReading(UUID.randomUUID(), "Mohan", EditorialLevel.B1, "Culture, Arts & Fiction");
    when(readings.browsePlatformReadings(
            null,
            null,
            null,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(progress.findByUserIdAndReadingIds(userId, Set.of(reading.id()))).thenReturn(Map.of());
    when(personalizer.personalizeReadings(eq(userId), eq("en"), eq(List.of(reading)), any()))
        .thenReturn(List.of(recommendedReading(reading, new BigDecimal("90.00"))));

    var query =
        new BrowsePlatformReadingsQuery(null, null, null, null, null, null, new PageRequest(0, 10));
    var result = useCase.browsePlatformReadings(query);

    assertThat(result.content()).hasSize(1);
    verify(readings)
        .browsePlatformReadings(
            null,
            null,
            null,
            null,
            null,
            PlatformReadingSort.DEFAULT,
            "en",
            new PageRequest(0, 10));
  }
}
