package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.model.VocabularyReviewHistoryEntry;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyReviewHistoryRepositoryPort;
import com.soap.soap.domain.model.ReviewAssessment;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordVocabularyReviewUseCaseTest {
  @Mock private CurrentUserPort currentUser;
  @Mock private UserVocabularyRepositoryPort repository;
  @Mock private UserVocabularyReviewHistoryRepositoryPort historyRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  private final FsrsScheduler scheduler = new FsrsScheduler();
  private RecordVocabularyReviewUseCase useCase;
  private final UUID userId = UUID.randomUUID();
  private final UUID wordId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase =
        new RecordVocabularyReviewUseCase(
            currentUser, repository, historyRepository, scheduler, clock);
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(() -> useCase.recordReview(null, ReviewRating.AGAIN))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(() -> useCase.recordReview(wordId, (ReviewRating) null))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }

  @Test
  void throwsWhenVocabularyEntryDoesNotExistOrBelongsToAnotherUser() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.recordReview(wordId, ReviewRating.GOOD))
        .isInstanceOf(VocabularyEntryNotFoundException.class);
  }

  @Test
  void recordsReviewAndSavesUpdatedEntryWithHistory() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "would", "en");
    var current =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(3),
            null,
            0L,
            0,
            null,
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(current));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewRating.GOOD);

    assertThat(result.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(result.srsState()).isEqualTo(SrsState.REVIEW);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusDays(4));
    assertThat(result.lastReviewedAt()).isEqualTo(nowUtc);
    assertThat(result.learnedAt()).isEqualTo(nowUtc);
    assertThat(result.repetitions()).isEqualTo(2);

    verify(repository).save(any(UserVocabulary.class));
    verify(historyRepository).recordReviewHistory(any(VocabularyReviewHistoryEntry.class));
  }

  @Test
  void recordsReviewWithLegacyAssessmentFallback() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    var user = new User(userId, "Ada", "ada@example.com");
    var word = new Word(wordId, "would", "en");
    var current =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(3),
            null,
            0L,
            0,
            null,
            nowUtc,
            SrsState.LEARNING,
            0.4872,
            7.6214,
            1,
            0);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(current));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewAssessment.FORGOT);

    assertThat(result.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(result.srsState()).isEqualTo(SrsState.LEARNING);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusSeconds(600L));
    assertThat(result.lastReviewedAt()).isEqualTo(nowUtc);

    verify(repository).save(any(UserVocabulary.class));
    verify(historyRepository).recordReviewHistory(any(VocabularyReviewHistoryEntry.class));
  }
}
