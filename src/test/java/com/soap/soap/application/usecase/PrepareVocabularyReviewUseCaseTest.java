package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.ReviewRating;
import com.soap.soap.domain.model.SrsState;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.domain.service.FsrsScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepareVocabularyReviewUseCaseTest {
  @Mock private CurrentUserPort currentUser;
  @Mock private UserVocabularyRepositoryPort repository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  private final FsrsScheduler scheduler = new FsrsScheduler();
  private PrepareVocabularyReviewUseCase useCase;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase = new PrepareVocabularyReviewUseCase(currentUser, repository, scheduler, clock);
  }

  @Test
  void rejectsInvalidBatchSizes() {
    for (int invalidSize : List.of(-1, 0, 101, 200)) {
      assertThatThrownBy(() -> useCase.prepareReview(invalidSize))
          .isInstanceOf(InvalidApplicationArgumentException.class)
          .hasMessageContaining("Review batch size must be between 1 and 100");
    }
  }

  @Test
  void preparesReviewWithPriorityCandidatesAndCounts() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    var user = new User(userId, "Ada", "ada@example.com");
    var word1 = new Word(UUID.randomUUID(), "would", "en");
    var word2 = new Word(UUID.randomUUID(), "could", "en");

    var uv1 =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word1,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(5),
            null,
            0L,
            0,
            null,
            nowUtc.minusHours(1),
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0);
    var uv2 =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word2,
            VocabularyStatus.KNOWN,
            nowUtc.minusDays(10),
            nowUtc.minusDays(10),
            0L,
            3,
            nowUtc.minusDays(14),
            nowUtc.minusHours(2),
            SrsState.REVIEW,
            13.8206,
            3.9320,
            3,
            0);

    when(repository.countDueWords(eq(userId), eq(nowUtc))).thenReturn(2L);
    when(repository.countTotalReviewableWords(eq(userId), eq(nowUtc))).thenReturn(2L);
    when(repository.findReviewCandidates(eq(userId), eq(nowUtc), eq(10)))
        .thenReturn(List.of(uv1, uv2));

    var result = useCase.prepareReview(10);

    assertThat(result.dueCount()).isEqualTo(2L);
    assertThat(result.totalReviewableCount()).isEqualTo(2L);
    assertThat(result.entries()).hasSize(2);

    var entry1 = result.entries().get(0);
    assertThat(entry1.wordId()).isEqualTo(word1.id());
    assertThat(entry1.word()).isEqualTo("would");
    assertThat(entry1.language()).isEqualTo("en");
    assertThat(entry1.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(entry1.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(entry1.ratingOptions()).hasSize(4);
    assertThat(
            entry1.ratingOptions().stream()
                .map(com.soap.soap.application.model.ReviewRatingOption::rating))
        .containsExactly(
            ReviewRating.AGAIN, ReviewRating.HARD, ReviewRating.GOOD, ReviewRating.EASY);

    var entry2 = result.entries().get(1);
    assertThat(entry2.wordId()).isEqualTo(word2.id());
    assertThat(entry2.word()).isEqualTo("could");
    assertThat(entry2.language()).isEqualTo("en");
    assertThat(entry2.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(entry2.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(entry2.ratingOptions()).hasSize(4);

    verify(repository).findReviewCandidates(userId, nowUtc, 10);
  }

  @Test
  void prepareReviewReturnsPartialBatchWhenOnlyFourReviewableCandidatesExist() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    var user = new User(userId, "Ada", "ada@example.com");
    var items = new java.util.ArrayList<UserVocabulary>();
    for (int i = 1; i <= 4; i++) {
      var w = new Word(UUID.randomUUID(), "word" + i, "en");
      items.add(
          new UserVocabulary(
              UUID.randomUUID(),
              user,
              w,
              VocabularyStatus.LEARNING,
              nowUtc.minusDays(i),
              null,
              0L,
              0,
              null,
              nowUtc.minusMinutes(i),
              SrsState.LEARNING,
              0.4872,
              7.6214,
              0,
              0));
    }

    when(repository.countDueWords(eq(userId), eq(nowUtc))).thenReturn(4L);
    when(repository.countTotalReviewableWords(eq(userId), eq(nowUtc))).thenReturn(4L);
    when(repository.findReviewCandidates(eq(userId), eq(nowUtc), eq(15))).thenReturn(items);

    var result = useCase.prepareReview(15);

    assertThat(result.dueCount()).isEqualTo(4L);
    assertThat(result.totalReviewableCount()).isEqualTo(4L);
    assertThat(result.entries()).hasSize(4);
  }
}
