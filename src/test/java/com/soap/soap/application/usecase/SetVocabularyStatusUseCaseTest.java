package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.SetVocabularyStatusCommand;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.WordResolver;
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
class SetVocabularyStatusUseCaseTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

  @Mock private UserRepositoryPort users;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private WordResolver wordResolver;
  @Mock private CurrentUserPort currentUser;

  private User user;
  private Word word;
  private SetVocabularyStatusUseCase useCase;

  @BeforeEach
  void setUp() {
    user = new User(UUID.randomUUID(), "Ada", "ada@example.com");
    word = new Word(UUID.randomUUID(), "learning", "en");
    when(currentUser.requireUserId()).thenReturn(user.id());
    when(users.findById(user.id())).thenReturn(Optional.of(user));
    when(wordResolver.resolve("learning", "en")).thenReturn(word);
    useCase =
        new SetVocabularyStatusUseCase(
            users,
            vocabulary,
            new TextWordProcessor(),
            new LanguageNormalizer(),
            wordResolver,
            CLOCK,
            currentUser,
            InputLimits.defaults());
  }

  @Test
  void createsAnExplicitStatusWhenTheWordWasPreviouslyUnclassified() {
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id())).thenReturn(Optional.empty());
    when(vocabulary.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("Learning", "EN", VocabularyStatus.LEARNING));

    assertThat(result.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(result.firstSeenAt()).isEqualTo(LocalDateTime.parse("2026-08-30T12:00:00"));
    assertThat(result.learnedAt()).isNull();
    verify(vocabulary).save(result);
  }

  @Test
  void updatesAnExistingStatusWithoutChangingItsFirstSeenDate() {
    var firstSeen = LocalDateTime.parse("2026-08-01T09:00:00");
    var existing =
        new UserVocabulary(
            UUID.randomUUID(), user, word, VocabularyStatus.KNOWN, firstSeen, firstSeen, 3L);
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id())).thenReturn(Optional.of(existing));
    when(vocabulary.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("learning", "en", VocabularyStatus.IGNORED));

    assertThat(result.id()).isEqualTo(existing.id());
    assertThat(result.status()).isEqualTo(VocabularyStatus.IGNORED);
    assertThat(result.firstSeenAt()).isEqualTo(firstSeen);
    assertThat(result.learnedAt()).isNull();
    assertThat(result.version()).isEqualTo(3L);
  }

  @Test
  void persistsExplicitNewAndKnownWithTheirCorrectLearnedDateSemantics() {
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id())).thenReturn(Optional.empty());
    when(vocabulary.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var explicitNew =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("learning", "en", VocabularyStatus.NEW));
    var known =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("learning", "en", VocabularyStatus.KNOWN));

    assertThat(explicitNew.status()).isEqualTo(VocabularyStatus.NEW);
    assertThat(explicitNew.learnedAt()).isNull();
    assertThat(explicitNew.reviewStage()).isEqualTo(0);
    assertThat(explicitNew.nextReviewAt()).isNull();

    assertThat(known.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(known.learnedAt()).isEqualTo(LocalDateTime.parse("2026-08-30T12:00:00"));
    // SRS V2: reviewStage is always 0 for new entries — it is a deprecated V1 field.
    assertThat(known.reviewStage()).isEqualTo(0);
    // nextReviewAt derived from initial stability (13.8206 → round → 14 days) — SRS V2 policy.
    assertThat(known.nextReviewAt())
        .isEqualTo(LocalDateTime.parse("2026-08-30T12:00:00").plusDays(14));
  }

  @Test
  void manualStatusTransitionsScheduleReviewsAccordingToDomainRules() {
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id())).thenReturn(Optional.empty());
    when(vocabulary.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var nowUtc = LocalDateTime.parse("2026-08-30T12:00:00");

    // 1. Manual LEARNING on brand new: stage 0, due immediately
    var learning =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("learning", "en", VocabularyStatus.LEARNING));
    assertThat(learning.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(learning.reviewStage()).isEqualTo(0);
    assertThat(learning.nextReviewAt()).isEqualTo(nowUtc);
    assertThat(learning.lastReviewedAt()).isNull();

    // 2. Manual NEW on brand new: stage 0, nextReviewAt null
    var newEntry =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("learning", "en", VocabularyStatus.NEW));
    assertThat(newEntry.status()).isEqualTo(VocabularyStatus.NEW);
    assertThat(newEntry.reviewStage()).isEqualTo(0);
    assertThat(newEntry.nextReviewAt()).isNull();
    assertThat(newEntry.lastReviewedAt()).isNull();

    // 3. Manual IGNORED on brand new: stage 0, nextReviewAt null
    var ignored =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("learning", "en", VocabularyStatus.IGNORED));
    assertThat(ignored.status()).isEqualTo(VocabularyStatus.IGNORED);
    assertThat(ignored.reviewStage()).isEqualTo(0);
    assertThat(ignored.nextReviewAt()).isNull();
    assertThat(ignored.lastReviewedAt()).isNull();

    // 4. Manual KNOWN on existing LEARNING entry: SRS V2 derives nextReviewAt from stability,
    // NOT from Leitner reviewStage. existingStage4 has stability = defaultStabilityForStatus
    // (LEARNING) = 0.4872 (set by the legacy constructor). SRS V2: round(0.4872) = 0 → max(1) =
    // +1 day. The Leitner "+30 days for stage 4" logic is eliminated.
    var existingStage4 =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.LEARNING,
            nowUtc.minusDays(30),
            null,
            0L,
            4,
            nowUtc.minusDays(5),
            nowUtc);
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id()))
        .thenReturn(Optional.of(existingStage4));

    var updatedStage4 =
        useCase.setVocabularyStatus(
            new SetVocabularyStatusCommand("learning", "en", VocabularyStatus.KNOWN));
    assertThat(updatedStage4.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(updatedStage4.srsState()).isEqualTo(existingStage4.srsState());
    // reviewStage is passed through unchanged — legacy field, NOT used for scheduling.
    assertThat(updatedStage4.reviewStage()).isEqualTo(4);
    // FASE 14.3.7.1 Decoupling: changeStatus(KNOWN) preserves active SRS schedule
    assertThat(updatedStage4.nextReviewAt()).isEqualTo(existingStage4.nextReviewAt());
    assertThat(updatedStage4.lastReviewedAt()).isEqualTo(nowUtc.minusDays(5));
  }
}
