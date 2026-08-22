package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.InitialVocabularyTest;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.InitialVocabularyTestSourcePort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InitialVocabularyTestUseCasesTest {
  @Mock private UserRepositoryPort users;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private WordRepositoryPort words;
  @Mock private CurrentUserPort currentUser;
  @Mock private InitialVocabularyTestSourcePort source;

  private final UUID userId = UUID.randomUUID();
  private final User user = new User(userId, "Ada", "ada@example.com");
  private final InitialVocabularyTest test =
      new InitialVocabularyTest("v1", "Hello bright world", List.of("hello", "bright", "world"));
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    when(currentUser.requireUserId()).thenReturn(userId);
  }

  @Test
  void getsTheTestForTheAuthenticatedUser() {
    when(users.existsById(userId)).thenReturn(true);
    when(source.load()).thenReturn(test);

    assertThat(
            new GetInitialVocabularyTestUseCase(users, source, currentUser)
                .getInitialVocabularyTest())
        .isEqualTo(test);
  }

  @Test
  void persistsOnlySelectedWordsAsKnownAndDeduplicatesSelections() {
    var hello = new Word(UUID.randomUUID(), "hello", "en");
    prepareCompletion();
    when(words.findByNormalizedValueAndLanguage("hello", "en")).thenReturn(Optional.of(hello));
    when(vocabulary.findByUserIdAndWordId(userId, hello.id())).thenReturn(Optional.empty());
    when(vocabulary.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = useCase().completeInitialVocabularyTest("v1", List.of("Hello", "hello"));

    assertThat(result.confirmedWordCount()).isEqualTo(1);
    assertThat(result.knownWords()).containsExactly("hello");
    verify(vocabulary).save(any(UserVocabulary.class));
    verify(words, never()).findByNormalizedValueAndLanguage("world", "en");
  }

  @Test
  void rejectsAWordOutsideTheTestBeforePersistingAnything() {
    prepareCompletion();

    assertThatThrownBy(() -> useCase().completeInitialVocabularyTest("v1", List.of("injected")))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    verify(vocabulary, never()).save(any());
    verify(words, never()).findByNormalizedValueAndLanguage(any(), any());
  }

  @Test
  void keepsAnExplicitlyIgnoredWordIgnored() {
    var hello = new Word(UUID.randomUUID(), "hello", "en");
    var ignored =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            hello,
            VocabularyStatus.IGNORED,
            LocalDateTime.now(clock),
            null);
    prepareCompletion();
    when(words.findByNormalizedValueAndLanguage("hello", "en")).thenReturn(Optional.of(hello));
    when(vocabulary.findByUserIdAndWordId(userId, hello.id())).thenReturn(Optional.of(ignored));

    var result = useCase().completeInitialVocabularyTest("v1", List.of("hello"));

    assertThat(result.confirmedWordCount()).isZero();
    verify(vocabulary, never()).save(any());
  }

  private void prepareCompletion() {
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(source.load()).thenReturn(test);
  }

  private CompleteInitialVocabularyTestUseCase useCase() {
    return new CompleteInitialVocabularyTestUseCase(
        users,
        vocabulary,
        source,
        new TextWordProcessor(),
        new WordResolver(words),
        clock,
        currentUser,
        InputLimits.defaults());
  }
}
