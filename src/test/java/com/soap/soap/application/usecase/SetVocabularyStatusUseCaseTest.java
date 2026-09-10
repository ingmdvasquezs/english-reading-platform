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
    assertThat(known.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(known.learnedAt()).isEqualTo(LocalDateTime.parse("2026-08-30T12:00:00"));
  }
}
