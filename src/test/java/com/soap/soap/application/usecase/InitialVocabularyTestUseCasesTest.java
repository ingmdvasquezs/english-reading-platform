package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.OnboardingAlreadyCompletedException;
import com.soap.soap.application.model.InitialVocabularyTest;
import com.soap.soap.application.model.InitialVocabularyTestResult;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.VocabularyClassification;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
      new InitialVocabularyTest(
          "v1",
          "Hello bright world alpha beta gamma delta epsilon zeta theta iota kappa",
          List.of(
              "hello", "bright", "world", "alpha", "beta", "gamma", "delta", "epsilon", "zeta",
              "theta", "iota", "kappa"));
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
  void rejectsDuplicateNormalizedClassifications() {
    prepareCompletion();

    assertThatThrownBy(
            () ->
                useCase()
                    .completeInitialVocabularyTest(
                        "v1",
                        List.of(
                            classification("Hello", VocabularyStatus.KNOWN),
                            classification("hello", VocabularyStatus.KNOWN))))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("more than once");

    verify(vocabulary, never()).saveAll(any());
  }

  @Test
  void persistsEveryExplicitStatusAndReportsKnownWordsSeparately() {
    prepareCompletion();
    stubResolvedWords(Map.of());
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Map.of());
    when(users.markOnboardingCompleted(userId)).thenReturn(true);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<UserVocabulary>> captor = ArgumentCaptor.forClass(Collection.class);

    var result =
        useCase()
            .completeInitialVocabularyTest(
                "v1",
                minimumClassifications(
                    classification("Hello", VocabularyStatus.NEW),
                    classification("bright", VocabularyStatus.LEARNING),
                    classification("world", VocabularyStatus.KNOWN),
                    classification("alpha", VocabularyStatus.IGNORED)));

    assertThat(result.confirmedWordCount()).isEqualTo(10);
    assertThat(result.knownWords()).containsExactly("world");
    verify(vocabulary).saveAll(captor.capture());
    Collection<UserVocabulary> saved = captor.getValue();
    var byWord =
        saved.stream()
            .collect(
                java.util.stream.Collectors.toMap(uv -> uv.word().normalizedValue(), uv -> uv));

    var nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

    // 1. KNOWN: stage 3, +14d, learnedAt = nowUtc, lastReviewedAt null
    var knownEntry = byWord.get("world");
    assertThat(knownEntry.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(knownEntry.reviewStage()).isEqualTo(3);
    assertThat(knownEntry.learnedAt()).isEqualTo(nowUtc);
    assertThat(knownEntry.lastReviewedAt()).isNull();
    assertThat(knownEntry.nextReviewAt()).isEqualTo(nowUtc.plusDays(14));

    // 2. LEARNING: stage 0, nowUtc (due immediately), learnedAt null, lastReviewedAt null
    var learningEntry = byWord.get("bright");
    assertThat(learningEntry.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(learningEntry.reviewStage()).isEqualTo(0);
    assertThat(learningEntry.learnedAt()).isNull();
    assertThat(learningEntry.lastReviewedAt()).isNull();
    assertThat(learningEntry.nextReviewAt()).isEqualTo(nowUtc);

    // 3. NEW: stage 0, nextReviewAt null, learnedAt null, lastReviewedAt null
    var newEntry = byWord.get("hello");
    assertThat(newEntry.status()).isEqualTo(VocabularyStatus.NEW);
    assertThat(newEntry.reviewStage()).isEqualTo(0);
    assertThat(newEntry.learnedAt()).isNull();
    assertThat(newEntry.lastReviewedAt()).isNull();
    assertThat(newEntry.nextReviewAt()).isNull();

    // 4. IGNORED: stage 0, nextReviewAt null, learnedAt null, lastReviewedAt null
    var ignoredEntry = byWord.get("alpha");
    assertThat(ignoredEntry.status()).isEqualTo(VocabularyStatus.IGNORED);
    assertThat(ignoredEntry.reviewStage()).isEqualTo(0);
    assertThat(ignoredEntry.learnedAt()).isNull();
    assertThat(ignoredEntry.lastReviewedAt()).isNull();
    assertThat(ignoredEntry.nextReviewAt()).isNull();

    verify(vocabulary, never()).findByUserIdAndWordId(any(), any());
    verify(words).resolveAll(any(), eq("en"));
    verify(users).markOnboardingCompleted(userId);
  }

  @Test
  void existingEntryUsesChangeStatusPreservingStageWhenTransitioningToKnown() {
    var hello = new Word(UUID.randomUUID(), "hello", "en");
    var existingStage4 =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            hello,
            VocabularyStatus.LEARNING,
            LocalDateTime.now(clock).minusDays(20),
            null,
            0L,
            4,
            LocalDateTime.now(clock).minusDays(2),
            LocalDateTime.now(clock));
    prepareCompletion();
    stubResolvedWords(Map.of("hello", hello));
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any()))
        .thenReturn(Map.of(hello.id(), existingStage4));
    when(users.markOnboardingCompleted(userId)).thenReturn(true);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<UserVocabulary>> captor = ArgumentCaptor.forClass(Collection.class);

    useCase()
        .completeInitialVocabularyTest(
            "v1", minimumClassifications(classification("hello", VocabularyStatus.KNOWN)));

    verify(vocabulary).saveAll(captor.capture());
    Collection<UserVocabulary> saved = captor.getValue();
    var helloEntry =
        saved.stream()
            .filter(uv -> uv.word().normalizedValue().equals("hello"))
            .findFirst()
            .orElseThrow();
    assertThat(helloEntry.status()).isEqualTo(VocabularyStatus.KNOWN);
    assertThat(helloEntry.reviewStage()).isEqualTo(4);
    assertThat(helloEntry.nextReviewAt())
        .isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(30));
    assertThat(helloEntry.lastReviewedAt()).isEqualTo(LocalDateTime.now(clock).minusDays(2));
  }

  @Test
  void rejectsAWordOutsideTheTestBeforePersistingAnything() {
    prepareCompletion();

    assertThatThrownBy(
            () ->
                useCase()
                    .completeInitialVocabularyTest(
                        "v1", List.of(classification("injected", VocabularyStatus.NEW))))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    verify(vocabulary, never()).saveAll(any());
    verify(words, never()).resolveAll(any(), any());
  }

  @Test
  void updatesAnExistingEntryToTheExplicitStatus() {
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
    stubResolvedWords(Map.of("hello", hello));
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any()))
        .thenReturn(Map.of(hello.id(), ignored));
    when(users.markOnboardingCompleted(userId)).thenReturn(true);

    var result =
        useCase()
            .completeInitialVocabularyTest(
                "v1", minimumClassifications(classification("hello", VocabularyStatus.LEARNING)));

    assertThat(result.confirmedWordCount()).isEqualTo(10);
    verify(vocabulary).saveAll(any());
  }

  @Test
  void knownWordIsIdempotentAndUsesOnlyBatchRepositoryOperations() {
    var hello = new Word(UUID.randomUUID(), "hello", "en");
    var known =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            hello,
            VocabularyStatus.KNOWN,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock));
    prepareCompletion();
    stubResolvedWords(Map.of("hello", hello));
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any()))
        .thenReturn(Map.of(hello.id(), known));
    when(users.markOnboardingCompleted(userId)).thenReturn(true);

    var result =
        useCase()
            .completeInitialVocabularyTest(
                "v1", minimumClassifications(classification("hello", VocabularyStatus.KNOWN)));

    assertThat(result.knownWords()).containsExactly("hello");
    verify(vocabulary).saveAll(any());
    verify(vocabulary, never()).findByUserIdAndWordId(any(), any());
  }

  @Test
  void processesTheMaximumSelectionWithConstantBatchCalls() {
    var values = java.util.stream.IntStream.range(0, 100).mapToObj(i -> "word" + i).toList();
    var classifications =
        values.stream().map(value -> classification(value, VocabularyStatus.NEW)).toList();
    var resolved =
        values.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    value -> value, value -> new Word(UUID.randomUUID(), value, "en")));
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(source.load()).thenReturn(new InitialVocabularyTest("large", "text", values));
    when(words.resolveAll(any(), eq("en"))).thenReturn(resolved);
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Map.of());
    when(users.markOnboardingCompleted(userId)).thenReturn(true);

    var result = useCase().completeInitialVocabularyTest("large", classifications);

    assertThat(result.confirmedWordCount()).isEqualTo(100);
    verify(words).resolveAll(any(), eq("en"));
    verify(vocabulary).findByUserIdAndWordIds(eq(userId), any());
    verify(vocabulary).saveAll(any());
    verify(vocabulary, never()).findByUserIdAndWordId(any(), any());
  }

  @Test
  void rejectsAUserWhoAlreadyCompletedOnboardingWithoutChangingVocabulary() {
    var completed =
        new User(userId, "Ada", "ada@example.com", "hash", LocalDateTime.now(clock), true);
    when(users.findById(userId)).thenReturn(Optional.of(completed));

    assertThatThrownBy(
            () ->
                useCase()
                    .completeInitialVocabularyTest(
                        "v1", List.of(classification("hello", VocabularyStatus.KNOWN))))
        .isInstanceOf(OnboardingAlreadyCompletedException.class);

    verify(vocabulary, never()).saveAll(any());
    verify(users, never()).markOnboardingCompleted(any());
  }

  @Test
  void doesNotMarkOnboardingCompletedWhenVocabularyPersistenceFails() {
    var hello = new Word(UUID.randomUUID(), "hello", "en");
    prepareCompletion();
    stubResolvedWords(Map.of("hello", hello));
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Map.of());
    when(vocabulary.saveAll(any())).thenThrow(new IllegalStateException("database failure"));

    assertThatThrownBy(
            () ->
                useCase()
                    .completeInitialVocabularyTest(
                        "v1",
                        minimumClassifications(classification("hello", VocabularyStatus.KNOWN))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database failure");

    verify(users, never()).markOnboardingCompleted(any());
  }

  @Test
  void rejectsAConcurrentSecondCompletionAndReliesOnTransactionRollback() {
    prepareCompletion();
    stubResolvedWords(Map.of());
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Map.of());
    when(users.markOnboardingCompleted(userId)).thenReturn(false);

    assertThatThrownBy(
            () -> useCase().completeInitialVocabularyTest("v1", minimumClassifications()))
        .isInstanceOf(OnboardingAlreadyCompletedException.class);
  }

  @Test
  void rejectsZeroAndBelowMinimumWithoutWritingAnything() {
    prepareCompletion();

    assertThatThrownBy(() -> useCase().completeInitialVocabularyTest("v1", List.of()))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("At least 10");
    assertThatThrownBy(
            () ->
                useCase()
                    .completeInitialVocabularyTest(
                        "v1",
                        test.selectableWords().stream()
                            .limit(9)
                            .map(value -> classification(value, VocabularyStatus.NEW))
                            .toList()))
        .isInstanceOf(InvalidApplicationArgumentException.class);

    verify(words, never()).resolveAll(any(), any());
    verify(vocabulary, never()).saveAll(any());
    verify(users, never()).markOnboardingCompleted(any());
  }

  @Test
  void acceptsExactlyMinimumAndMoreThanMinimum() {
    prepareCompletion();
    stubResolvedWords(Map.of());
    when(vocabulary.findByUserIdAndWordIds(eq(userId), any())).thenReturn(Map.of());
    when(users.markOnboardingCompleted(userId)).thenReturn(true);

    assertThat(useCase().completeInitialVocabularyTest("v1", minimumClassifications()))
        .extracting(InitialVocabularyTestResult::confirmedWordCount)
        .isEqualTo(10);
    assertThat(
            useCase()
                .completeInitialVocabularyTest(
                    "v1",
                    test.selectableWords().stream()
                        .limit(11)
                        .map(value -> classification(value, VocabularyStatus.NEW))
                        .toList()))
        .extracting(InitialVocabularyTestResult::confirmedWordCount)
        .isEqualTo(11);
  }

  @Test
  void normalizedDuplicatesCannotSatisfyTheMinimum() {
    prepareCompletion();
    var duplicates =
        java.util.stream.IntStream.range(0, 10)
            .mapToObj(
                index -> classification(index % 2 == 0 ? "Hello" : "hello", VocabularyStatus.NEW))
            .toList();

    assertThatThrownBy(() -> useCase().completeInitialVocabularyTest("v1", duplicates))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessageContaining("more than once");
    verify(vocabulary, never()).saveAll(any());
    verify(users, never()).markOnboardingCompleted(any());
  }

  private VocabularyClassification classification(String word, VocabularyStatus status) {
    return new VocabularyClassification(word, status);
  }

  private List<VocabularyClassification> minimumClassifications(
      VocabularyClassification... preferred) {
    var result = new java.util.LinkedHashMap<String, VocabularyClassification>();
    for (var classification : preferred) {
      result.put(classification.word().toLowerCase(), classification);
    }
    for (var value : test.selectableWords()) {
      result.putIfAbsent(value, classification(value, VocabularyStatus.NEW));
      if (result.size()
          == CompleteInitialVocabularyTestUseCase.MINIMUM_ONBOARDING_CLASSIFICATIONS) {
        break;
      }
    }
    return List.copyOf(result.values());
  }

  @SuppressWarnings("unchecked")
  private void stubResolvedWords(Map<String, Word> overrides) {
    when(words.resolveAll(any(), eq("en")))
        .thenAnswer(
            invocation -> {
              var values = (java.util.Collection<String>) invocation.getArgument(0);
              return values.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          value -> value,
                          value ->
                              overrides.getOrDefault(
                                  value, new Word(UUID.randomUUID(), value, "en"))));
            });
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
