package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.VocabularyCompatibilityCalculator;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListUserReadingsMetricsTest {
  @Mock private UserRepositoryPort users;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private CurrentUserPort currentUser;

  private User user;
  private ListUserReadingsUseCase useCase;

  @BeforeEach
  void setUp() {
    user = new User(UUID.randomUUID(), "Ada", "ada@example.com");
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.existsById(user.id())).thenReturn(true);
    useCase =
        new ListUserReadingsUseCase(
            users,
            readings,
            progress,
            vocabulary,
            new TextWordProcessor(),
            new VocabularyCompatibilityCalculator(),
            currentUser);
  }

  @Test
  void calculatesTwoReadingBreakdownsWithOneBatchQueryForTheirLanguage() {
    var first = reading("First", "Work work WORK learning new ignored absent", 2);
    var second = reading("Second", "work OTHER", 1);
    var request = new PageRequest(0, 10);
    when(readings.findUserReadingsByUserId(user.id(), request))
        .thenReturn(new PageResult<>(List.of(first, second), 0, 10, 2));
    when(progress.findByUserIdAndReadingIds(user.id(), Set.of(first.id(), second.id())))
        .thenReturn(
            Map.of(
                first.id(),
                ReadingProgress.inProgress(
                    user.id(), first.id(), LocalDateTime.parse("2026-08-30T09:00:00"))));
    var union = Set.of("work", "learning", "new", "ignored", "absent", "other");
    when(vocabulary.findStatusesByNormalizedValues(user.id(), "en", union))
        .thenReturn(
            Map.of(
                "work", VocabularyStatus.KNOWN,
                "learning", VocabularyStatus.LEARNING,
                "new", VocabularyStatus.NEW,
                "ignored", VocabularyStatus.IGNORED));

    var result = useCase.listUserReadings(request);

    assertThat(result.totalElements()).isEqualTo(2);
    assertThat(result.content().getFirst())
        .satisfies(
            summary -> {
              assertThat(summary.uniqueWords()).isEqualTo(5);
              assertThat(summary.knownWords()).isEqualTo(1);
              assertThat(summary.learningWords()).isEqualTo(1);
              assertThat(summary.explicitNewWords()).isEqualTo(1);
              assertThat(summary.ignoredWords()).isEqualTo(1);
              assertThat(summary.unclassifiedWords()).isEqualTo(1);
              assertThat(summary.vocabularyFitPercentage()).isEqualByComparingTo("56.00");
              assertThat(summary.classificationConfidencePercentage())
                  .isEqualByComparingTo("80.00");
              assertThat(summary.progressStatus()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);
            });
    assertThat(result.content().get(1))
        .satisfies(
            summary -> {
              assertThat(summary.uniqueWords()).isEqualTo(2);
              assertThat(summary.knownWords()).isEqualTo(1);
              assertThat(summary.unclassifiedWords()).isEqualTo(1);
              assertThat(summary.vocabularyFitPercentage()).isEqualByComparingTo("65.00");
              assertThat(summary.classificationConfidencePercentage())
                  .isEqualByComparingTo("50.00");
              assertThat(summary.progressStatus()).isNull();
            });
    verify(vocabulary, times(1)).findStatusesByNormalizedValues(user.id(), "en", union);
    verify(progress, times(1))
        .findByUserIdAndReadingIds(user.id(), Set.of(first.id(), second.id()));
  }

  @Test
  void userWithoutVocabularyGetsOnlyUnclassifiedUniqueWords() {
    var reading = reading("No vocabulary", "One one TWO", 1);
    var request = new PageRequest(1, 5);
    when(readings.findUserReadingsByUserId(user.id(), request))
        .thenReturn(new PageResult<>(List.of(reading), 1, 5, 6));
    when(vocabulary.findStatusesByNormalizedValues(user.id(), "en", Set.of("one", "two")))
        .thenReturn(Map.of());

    var result = useCase.listUserReadings(request);

    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(5);
    assertThat(result.totalElements()).isEqualTo(6);
    assertThat(result.content().getFirst().uniqueWords()).isEqualTo(2);
    assertThat(result.content().getFirst().knownWords()).isZero();
    assertThat(result.content().getFirst().learningWords()).isZero();
    assertThat(result.content().getFirst().explicitNewWords()).isZero();
    assertThat(result.content().getFirst().ignoredWords()).isZero();
    assertThat(result.content().getFirst().unclassifiedWords()).isEqualTo(2);
    assertThat(result.content().getFirst().vocabularyFitPercentage()).isEqualByComparingTo("30.00");
    assertThat(result.content().getFirst().classificationConfidencePercentage())
        .isEqualByComparingTo("0.00");
  }

  @Test
  void recalculatesCompatibilityAfterGlobalVocabularyChanges() {
    var reading = reading("Dynamic", "known absent", 1);
    var request = new PageRequest(0, 10);
    when(readings.findUserReadingsByUserId(user.id(), request))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(vocabulary.findStatusesByNormalizedValues(user.id(), "en", Set.of("known", "absent")))
        .thenReturn(Map.of())
        .thenReturn(Map.of("known", VocabularyStatus.KNOWN, "absent", VocabularyStatus.LEARNING));

    var before = useCase.listUserReadings(request).content().getFirst();
    var after = useCase.listUserReadings(request).content().getFirst();

    assertThat(before.vocabularyFitPercentage()).isEqualByComparingTo("30.00");
    assertThat(after.vocabularyFitPercentage()).isEqualByComparingTo("75.00");
    assertThat(after.classificationConfidencePercentage()).isEqualByComparingTo("100.00");
    verify(vocabulary, times(2))
        .findStatusesByNormalizedValues(user.id(), "en", Set.of("known", "absent"));
  }

  @Test
  void usesOnlyTheAuthenticatedUsersVocabulary() {
    var otherUser = new User(UUID.randomUUID(), "Grace", "grace@example.com");
    var reading = reading("Private metrics", "shared", 1);
    var request = new PageRequest(0, 10);
    when(currentUser.requireUserId()).thenReturn(user.id(), otherUser.id());
    when(users.existsById(otherUser.id())).thenReturn(true);
    when(readings.findUserReadingsByUserId(user.id(), request))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(readings.findUserReadingsByUserId(otherUser.id(), request))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));
    when(vocabulary.findStatusesByNormalizedValues(user.id(), "en", Set.of("shared")))
        .thenReturn(Map.of("shared", VocabularyStatus.KNOWN));
    when(vocabulary.findStatusesByNormalizedValues(otherUser.id(), "en", Set.of("shared")))
        .thenReturn(Map.of());

    var ownerResult = useCase.listUserReadings(request).content().getFirst();
    var otherResult = useCase.listUserReadings(request).content().getFirst();

    assertThat(ownerResult.vocabularyFitPercentage()).isEqualByComparingTo("100.00");
    assertThat(otherResult.vocabularyFitPercentage()).isEqualByComparingTo("30.00");
    verify(vocabulary).findStatusesByNormalizedValues(user.id(), "en", Set.of("shared"));
    verify(vocabulary).findStatusesByNormalizedValues(otherUser.id(), "en", Set.of("shared"));
  }

  private Reading reading(String title, String content, int minute) {
    return new Reading(
        UUID.randomUUID(),
        user,
        title,
        content,
        "en",
        LocalDateTime.parse("2026-08-30T10:00:00").plusMinutes(minute));
  }
}
