package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
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
  private PrepareVocabularyReviewUseCase useCase;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase = new PrepareVocabularyReviewUseCase(currentUser, repository, clock);
  }

  @Test
  void rejectsInvalidBatchSizes() {
    for (int invalidSize : List.of(-1, 0, 5, 15, 25, 35, 50)) {
      assertThatThrownBy(() -> useCase.prepareReview(invalidSize))
          .isInstanceOf(InvalidApplicationArgumentException.class)
          .hasMessageContaining("Review batch size must be 10, 20, or 30");
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
            nowUtc.minusHours(1));
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
            nowUtc.minusHours(2));

    when(repository.countDueWords(eq(userId), eq(nowUtc))).thenReturn(2L);
    when(repository.countTotalReviewableWords(eq(userId), eq(nowUtc))).thenReturn(2L);
    when(repository.findReviewCandidates(eq(userId), eq(nowUtc), eq(10)))
        .thenReturn(List.of(uv1, uv2));

    var result = useCase.prepareReview(10);

    assertThat(result.dueCount()).isEqualTo(2L);
    assertThat(result.totalReviewableCount()).isEqualTo(2L);
    assertThat(result.entries()).hasSize(2);
    assertThat(result.entries().get(0).wordId()).isEqualTo(word1.id());
    assertThat(result.entries().get(0).word()).isEqualTo("would");
    assertThat(result.entries().get(0).language()).isEqualTo("en");
    assertThat(result.entries().get(0).status()).isEqualTo(VocabularyStatus.LEARNING);

    assertThat(result.entries().get(1).wordId()).isEqualTo(word2.id());
    assertThat(result.entries().get(1).word()).isEqualTo("could");
    assertThat(result.entries().get(1).language()).isEqualTo("en");
    assertThat(result.entries().get(1).status()).isEqualTo(VocabularyStatus.KNOWN);

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
              nowUtc.minusMinutes(i)));
    }

    when(repository.countDueWords(eq(userId), eq(nowUtc))).thenReturn(4L);
    when(repository.countTotalReviewableWords(eq(userId), eq(nowUtc))).thenReturn(4L);
    when(repository.findReviewCandidates(eq(userId), eq(nowUtc), eq(10))).thenReturn(items);

    var result = useCase.prepareReview(10);

    assertThat(result.dueCount()).isEqualTo(4L);
    assertThat(result.totalReviewableCount()).isEqualTo(4L);
    assertThat(result.entries()).hasSize(4);
  }
}
