package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.ReviewAssessment;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
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

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC);
  private RecordVocabularyReviewUseCase useCase;
  private final UUID userId = UUID.randomUUID();
  private final UUID wordId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    useCase = new RecordVocabularyReviewUseCase(currentUser, repository, clock);
  }

  @Test
  void rejectsNullArguments() {
    assertThatThrownBy(() -> useCase.recordReview(null, ReviewAssessment.FORGOT))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(() -> useCase.recordReview(wordId, null))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }

  @Test
  void throwsWhenVocabularyEntryDoesNotExistOrBelongsToAnotherUser() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.recordReview(wordId, ReviewAssessment.REMEMBERED))
        .isInstanceOf(VocabularyEntryNotFoundException.class);
  }

  @Test
  void recordsReviewAndSavesUpdatedEntry() {
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
            nowUtc);

    when(repository.findByUserIdAndWordId(userId, wordId)).thenReturn(Optional.of(current));
    when(repository.save(any(UserVocabulary.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase.recordReview(wordId, ReviewAssessment.REMEMBERED);

    assertThat(result.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(result.reviewStage()).isEqualTo(1);
    assertThat(result.nextReviewAt()).isEqualTo(nowUtc.plusDays(3));
    assertThat(result.lastReviewedAt()).isEqualTo(nowUtc);
    assertThat(result.learnedAt()).isEqualTo(nowUtc);

    verify(repository).save(any(UserVocabulary.class));
  }
}
