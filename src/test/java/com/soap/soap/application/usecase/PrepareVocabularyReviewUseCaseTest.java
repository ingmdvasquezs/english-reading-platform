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
            VocabularyStatus.NEW,
            nowUtc.minusDays(1),
            null,
            0L,
            0,
            null,
            null);

    when(repository.countDueWords(eq(userId), eq(nowUtc))).thenReturn(1L);
    when(repository.countTotalReviewableWords(eq(userId), eq(nowUtc))).thenReturn(2L);
    when(repository.findReviewCandidates(eq(userId), eq(nowUtc), eq(10)))
        .thenReturn(List.of(uv1, uv2));

    var result = useCase.prepareReview(10);

    assertThat(result.dueCount()).isEqualTo(1L);
    assertThat(result.totalReviewableCount()).isEqualTo(2L);
    assertThat(result.entries()).hasSize(2);
    assertThat(result.entries().get(0).wordId()).isEqualTo(word1.id());
    assertThat(result.entries().get(0).word()).isEqualTo("would");
    assertThat(result.entries().get(0).language()).isEqualTo("en");
    assertThat(result.entries().get(0).status()).isEqualTo(VocabularyStatus.LEARNING);

    assertThat(result.entries().get(1).wordId()).isEqualTo(word2.id());
    assertThat(result.entries().get(1).word()).isEqualTo("could");
    assertThat(result.entries().get(1).language()).isEqualTo("en");
    assertThat(result.entries().get(1).status()).isEqualTo(VocabularyStatus.NEW);

    verify(repository).findReviewCandidates(userId, nowUtc, 10);
  }
}
