package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.AddWordToVocabularyCommand;
import com.soap.soap.application.command.RegisterReadingCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.port.out.WordRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import com.soap.soap.application.service.ReadingAnalyzer;
import com.soap.soap.application.service.ReadingEditorialAccessPolicy;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.VocabularyCompatibilityCalculator;
import com.soap.soap.application.service.WordResolver;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationUseCasesTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-18T15:00:00Z"), ZoneOffset.UTC);

  @Mock private UserRepositoryPort users;
  @Mock private WordRepositoryPort words;
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private UserVocabularyRepositoryPort vocabulary;
  @Mock private CurrentUserPort currentUser;

  private User user;
  private TextWordProcessor processor;
  private ReadingEditorialAccessPolicy accessPolicy;

  @BeforeEach
  void setUp() {
    user = new User(UUID.randomUUID(), "Ada", "ada@example.com");
    processor = new TextWordProcessor();
    accessPolicy = new ReadingEditorialAccessPolicy(progress);
    lenient().when(currentUser.requireUserId()).thenReturn(user.id());
  }

  @Test
  void registersATrimmedReadingForAnExistingUser() {
    when(users.findById(user.id())).thenReturn(Optional.of(user));
    when(readings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var useCase =
        new RegisterReadingUseCase(
            users, readings, new LanguageNormalizer(), currentUser, InputLimits.defaults());

    var result =
        useCase.registerReading(new RegisterReadingCommand("  Title  ", "  Content  ", " EN "));

    assertThat(result.title()).isEqualTo("Title");
    assertThat(result.content()).isEqualTo("Content");
    assertThat(result.language()).isEqualTo("en");
    assertThat(result.origin()).isEqualTo(ReadingOrigin.USER);
    assertThat(result.user()).isEqualTo(user);
    assertThat(result.editorialLevel()).isNull();
    assertThat(result.category()).isNull();
    assertThat(result.editorialStatus()).isNull();
  }

  @Test
  void rejectsRegistrationForAnUnknownUser() {
    var userId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new RegisterReadingUseCase(
                        users,
                        readings,
                        new LanguageNormalizer(),
                        currentUser,
                        InputLimits.defaults())
                    .registerReading(new RegisterReadingCommand("Title", "Content", "en")))
        .isInstanceOf(UserNotFoundException.class);
    verify(readings, never()).save(any());
  }

  @Test
  void addsANormalizedKnownWordAndSetsLearnedAt() {
    var word = new Word(UUID.randomUUID(), "don't", "en");
    when(users.findById(user.id())).thenReturn(Optional.of(user));
    when(words.resolve("don't", "en")).thenReturn(word);
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id())).thenReturn(Optional.empty());
    when(vocabulary.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var useCase =
        new AddWordToVocabularyUseCase(
            users,
            vocabulary,
            processor,
            new LanguageNormalizer(),
            new WordResolver(words),
            CLOCK,
            currentUser,
            InputLimits.defaults());

    var result =
        useCase.addWordToVocabulary(
            new AddWordToVocabularyCommand(" DON’T ", "EN", VocabularyStatus.KNOWN));

    assertThat(result.word()).isEqualTo(word);
    assertThat(result.learnedAt()).isEqualTo(LocalDateTime.now(CLOCK));
  }

  @Test
  void rejectsADuplicateVocabularyEntry() {
    var word = new Word(UUID.randomUUID(), "hello", "en");
    var existing =
        new UserVocabulary(
            UUID.randomUUID(), user, word, VocabularyStatus.NEW, LocalDateTime.now(CLOCK), null);
    when(users.findById(user.id())).thenReturn(Optional.of(user));
    when(words.resolve("hello", "en")).thenReturn(word);
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id())).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                new AddWordToVocabularyUseCase(
                        users,
                        vocabulary,
                        processor,
                        new LanguageNormalizer(),
                        new WordResolver(words),
                        CLOCK,
                        currentUser,
                        InputLimits.defaults())
                    .addWordToVocabulary(
                        new AddWordToVocabularyCommand("hello", "en", VocabularyStatus.NEW)))
        .isInstanceOf(WordAlreadyInVocabularyException.class);
    verify(vocabulary, never()).save(any());
  }

  @Test
  void changingAwayFromKnownClearsLearnedAt() {
    var word = new Word(UUID.randomUUID(), "hello", "en");
    var current =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            word,
            VocabularyStatus.KNOWN,
            LocalDateTime.now(CLOCK).minusDays(1),
            LocalDateTime.now(CLOCK));
    when(users.existsById(user.id())).thenReturn(true);
    when(vocabulary.findByUserIdAndWordId(user.id(), word.id())).thenReturn(Optional.of(current));
    when(vocabulary.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var changed =
        new ChangeVocabularyStatusUseCase(users, vocabulary, CLOCK, currentUser)
            .changeVocabularyStatus(word.id(), VocabularyStatus.LEARNING);

    assertThat(changed.status()).isEqualTo(VocabularyStatus.LEARNING);
    assertThat(changed.learnedAt()).isNull();
  }

  @Test
  void analyzesRepeatedTokensWithOneDeduplicatedBatchLookup() {
    var reading =
        new Reading(
            UUID.randomUUID(), user, "Title", "Hello, unknown HELLO!", "en", LocalDateTime.now());
    when(users.existsById(user.id())).thenReturn(true);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    when(vocabulary.findStatusesByNormalizedValues(user.id(), "en", Set.of("hello", "unknown")))
        .thenReturn(Map.of("hello", VocabularyStatus.KNOWN));

    var analysis =
        new AnalyzeReadingUseCase(
                users,
                readings,
                vocabulary,
                processor,
                new ReadingAnalyzer(),
                accessPolicy,
                currentUser)
            .analyzeReading(reading.id());

    assertThat(analysis.totalTokens()).isEqualTo(3);
    assertThat(analysis.knownTokens()).isEqualTo(2);
    assertThat(analysis.personalizedCoveragePercentage()).isCloseTo(66.67, within(0.01));
    verify(vocabulary).findStatusesByNormalizedValues(user.id(), "en", Set.of("hello", "unknown"));
  }

  @Test
  void refusesToAnalyzeAnotherUsersReading() {
    var owner = new User(UUID.randomUUID(), "Grace", "grace@example.com");
    var reading =
        new Reading(UUID.randomUUID(), owner, "Title", "Hello", "en", LocalDateTime.now());
    when(users.existsById(user.id())).thenReturn(true);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));

    assertThatThrownBy(
            () ->
                new AnalyzeReadingUseCase(
                        users,
                        readings,
                        vocabulary,
                        processor,
                        new ReadingAnalyzer(),
                        accessPolicy,
                        currentUser)
                    .analyzeReading(reading.id()))
        .isInstanceOf(ReadingNotFoundException.class);
    verify(vocabulary, never()).findStatusesByNormalizedValues(any(), any(), any());
  }

  @Test
  void analyzesAPlatformReadingForAnAuthenticatedUser() {
    var reading =
        new Reading(
            UUID.randomUUID(),
            null,
            "Platform",
            "Hello world",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.A2,
            "Science",
            EditorialStatus.PUBLISHED);
    when(users.existsById(user.id())).thenReturn(true);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    when(vocabulary.findStatusesByNormalizedValues(user.id(), "en", Set.of("hello", "world")))
        .thenReturn(Map.of("hello", VocabularyStatus.KNOWN));

    var analysis =
        new AnalyzeReadingUseCase(
                users,
                readings,
                vocabulary,
                processor,
                new ReadingAnalyzer(),
                accessPolicy,
                currentUser)
            .analyzeReading(reading.id());

    assertThat(analysis.knownWords()).isEqualTo(1);
    assertThat(analysis.unknownWords()).isEqualTo(1);
  }

  @Test
  void listsReadingsUsingApplicationPagination() {
    var request = new PageRequest(1, 10);
    var source = new PageResult<Reading>(List.of(), 1, 10, 12);
    var expected = new PageResult<ReadingSummary>(List.of(), 1, 10, 12);
    when(users.existsById(user.id())).thenReturn(true);
    when(readings.findUserReadingsByUserId(user.id(), request)).thenReturn(source);

    assertThat(
            new ListUserReadingsUseCase(
                    users,
                    readings,
                    progress,
                    vocabulary,
                    processor,
                    new VocabularyCompatibilityCalculator(),
                    currentUser)
                .listUserReadings(request))
        .isEqualTo(expected);
  }

  @Test
  void listsOnlyPlatformSummariesUsingApplicationPagination() {
    var request = new PageRequest(0, 10);
    var expected = new PageResult<PlatformReadingSummary>(List.of(), 0, 10, 4);
    when(users.existsById(user.id())).thenReturn(true);
    when(readings.findPlatformSummaries(request)).thenReturn(expected);

    assertThat(
            new ListPlatformReadingsUseCase(users, readings, progress, currentUser)
                .listPlatformReadings(request))
        .isEqualTo(expected);
    verify(readings).findPlatformSummaries(request);
  }

  @Test
  void rejectsAnalysisForAnUnknownUserUsingExistenceCheck() {
    when(users.existsById(user.id())).thenReturn(false);

    assertThatThrownBy(
            () ->
                new AnalyzeReadingUseCase(
                        users,
                        readings,
                        vocabulary,
                        processor,
                        new ReadingAnalyzer(),
                        accessPolicy,
                        currentUser)
                    .analyzeReading(UUID.randomUUID()))
        .isInstanceOf(UserNotFoundException.class);
    verify(users, never()).findById(any());
    verify(readings, never()).findById(any());
  }

  @Test
  void analyzesContentWithoutTokensWithoutVocabularyLookup() {
    var reading =
        new Reading(UUID.randomUUID(), user, "Title", "123 -- ...", "en", LocalDateTime.now());
    when(users.existsById(user.id())).thenReturn(true);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));

    var analysis =
        new AnalyzeReadingUseCase(
                users,
                readings,
                vocabulary,
                processor,
                new ReadingAnalyzer(),
                accessPolicy,
                currentUser)
            .analyzeReading(reading.id());

    assertThat(analysis.words()).isEmpty();
    verify(vocabulary, never()).findStatusesByNormalizedValues(any(), any(), any());
  }

  @Test
  void commandsRejectStructurallyInvalidArgumentsWithApplicationException() {
    assertThatThrownBy(() -> new RegisterReadingCommand(null, "Content", "en"))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(() -> new AddWordToVocabularyCommand(" ", "en", VocabularyStatus.LEARNING))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }
}
